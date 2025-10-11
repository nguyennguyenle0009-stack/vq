
package rt.client.tools;

import rt.common.world.WorldGenConfig;
import rt.common.world.WorldGenerator;
import rt.common.world.ChunkPos;
import rt.common.world.TerrainPalette;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * StaticMapExporterSharp
 *
 * Mục tiêu:
 *  - Vùng hiển thị theo **đường kính** (diameterTiles).
 *  - Điều chỉnh độ sắc nét bằng 2 tham số:
 *      * chunksPerPixel: 1 pixel đại diện cho block (chunksPerPixel × chunksPerPixel) chunk
 *      * chunkScale: mỗi pixel-đại-diện được phóng to thành block (chunkScale × chunkScale) pixel trong ảnh
 *    => kích thước ảnh cuối = ceil(spanChunks / chunksPerPixel) * chunkScale
 *  - Hiển thị lưới chunk: kẻ viền mỗi "block" (pixel đại diện), và nếu chunksPerPixel==1 thì có viền **từng chunk** thực sự.
 *  - Tính song song theo hàng + log tiến độ **mỗi hàng**.
 *
 * Dùng:
 *   java rt.tools.StaticMapExporterSharp [outPath] [diameterTiles] [chunksPerPixel] [chunkScale] [seed] [grid(0/1)]
 *
 * Mặc định:
 *   outPath         = static-map-100k-sharp.png
 *   diameterTiles   = 100_000                // đường kính vùng
 *   chunksPerPixel  = 1                      // 1 pixel = 1 chunk  (sắc nét theo chunk, nặng hơn)
 *   chunkScale      = 1                      // phóng đại mỗi pixel-đại-diện
 *   seed            = 1337
 *   grid            = 1                      // bật lưới
 *
 * Gợi ý hiệu năng:
 *   - Nếu chạy chậm, tăng chunksPerPixel lên 2 hoặc 4 (bọt pixel theo block chunk để giảm số lần generateChunk).
 *   - Hoặc giảm chunkScale (ảnh nhỏ hơn).
 *   - Hoặc giảm diameterTiles.
 */
public final class StaticMapExporterSharp {
    public static void main(String[] args) throws IOException {
        final String outPath        = (args.length >= 1 && !args[0].isBlank()) ? args[0] : "static-map-100k-sharp.png";
        final int diameterTiles     = (args.length >= 2) ? parseInt(args[1], 100_000) : 100_000;
        final int chunksPerPixel    = Math.max(1, (args.length >= 3) ? parseInt(args[2], 1) : 1);
        final int chunkScale        = Math.max(1, (args.length >= 4) ? parseInt(args[3], 1) : 1);
        final long seed             = (args.length >= 5) ? parseLong(args[4], 1337L) : 1337L;
        final boolean drawGrid      = (args.length >= 6) ? (parseInt(args[5], 1) != 0) : true;

        // Configure generator
        WorldGenConfig cfg = new WorldGenConfig(seed);
        WorldGenerator.configure(cfg);

        final int N = ChunkPos.SIZE; // tiles per chunk
        final int radiusTiles = Math.max(1, diameterTiles / 2);
        final int radiusChunks  = (int) Math.ceil(radiusTiles / (double) N);
        final int spanChunks    = radiusChunks * 2;
        final int blocksX       = divCeil(spanChunks, chunksPerPixel); // số "pixel-đại-diện" theo trục
        final int blocksY       = blocksX; // vuông
        final int widthPixels   = blocksX * chunkScale;
        final int heightPixels  = blocksY * chunkScale;

        final int startCx = -radiusChunks;
        final int startCy = -radiusChunks;

        System.out.printf(
            "Exporting sharp map: diameterTiles=%d, radiusChunks=%d, chunksPerPixel=%d, chunkScale=%d, output=%dx%d (blocks=%dx%d)\n",
            diameterTiles, radiusChunks, chunksPerPixel, chunkScale, widthPixels, heightPixels, blocksX, blocksY
        );
        final int totalBlocks = blocksX * blocksY;
        System.out.printf("Total representative pixels = %d (each => %dx chunk block)\n",
            totalBlocks, chunksPerPixel);

        // Prepare pixel buffer (ARGB)
        final int[] pixels = new int[widthPixels * heightPixels];

        // Thread-local working buffers per thread to avoid contention
        final ThreadLocal<byte[]> l1TL = ThreadLocal.withInitial(() -> new byte[N * N]);
        final ThreadLocal<byte[]> l2TL = ThreadLocal.withInitial(() -> new byte[N * N]);
        final ThreadLocal<BitSet> collTL = ThreadLocal.withInitial(() -> new BitSet(N * N));

        final AtomicInteger rowsDone = new AtomicInteger(0);

        // Parallel by output block-row (pyBlock)
        IntStream.range(0, blocksY).parallel().forEach(pyBlock -> {
            final int cy = startCy + pyBlock * chunksPerPixel;
            final byte[] l1 = l1TL.get();
            final byte[] l2 = l2TL.get();
            final BitSet coll = collTL.get();

            for (int pxBlock = 0; pxBlock < blocksX; pxBlock++) {
                final int cx = startCx + pxBlock * chunksPerPixel;

                Arrays.fill(l1, (byte) 0);
                Arrays.fill(l2, (byte) 0);
                coll.clear();

                WorldGenerator.generateChunk(cx, cy, N, l1, l2, coll, cfg);

                final int centerIdx = (N/2) * N + (N/2);
                final int terrainId = Byte.toUnsignedInt(l1[centerIdx]);
                final int argb = TerrainPalette.color(terrainId);

                // paint scaled block
                final int x0 = pxBlock * chunkScale;
                final int y0 = pyBlock * chunkScale;
                for (int dy = 0; dy < chunkScale; dy++) {
                    int rowOff = (y0 + dy) * widthPixels + x0;
                    for (int dx = 0; dx < chunkScale; dx++) {
                        pixels[rowOff + dx] = argb;
                    }
                }
            }

            int done = rowsDone.incrementAndGet();
            System.out.printf("Row %d / %d (%.1f%%)\n", done, blocksY, 100.0 * done / blocksY);
            System.out.flush();
        });

        // Draw grid overlay (simple 1px black lines).
        if (drawGrid) {
            final int gridColor = 0xFF000000; // black
            // Grid of representative blocks:
            // vertical
            for (int bx = 0; bx <= blocksX; bx++) {
                int x = Math.min(widthPixels - 1, bx * chunkScale);
                for (int y = 0; y < heightPixels; y++) {
                    pixels[y * widthPixels + x] = gridColor;
                }
            }
            // horizontal
            for (int by = 0; by <= blocksY; by++) {
                int y = Math.min(heightPixels - 1, by * chunkScale);
                int off = y * widthPixels;
                for (int x = 0; x < widthPixels; x++) {
                    pixels[off + x] = gridColor;
                }
            }

            // If chunksPerPixel==1 and chunkScale>1, the above grid is already per-chunk.
            // If chunksPerPixel>1, the grid represents "block" boundaries (không thể vẽ lưới từng chunk bên trong 1 pixel).
        }

        // Dump to image
        BufferedImage img = new BufferedImage(widthPixels, heightPixels, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, widthPixels, heightPixels, pixels, 0, widthPixels);

        File out = new File(outPath);
        ImageIO.write(img, "PNG", out);
        System.out.println("Saved: " + out.getAbsolutePath());
    }

    private static int divCeil(int a, int b) { return (a + b - 1) / b; }
    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim().replace("_","")); }
        catch (Exception e) { return fallback; }
    }
    private static long parseLong(String s, long fallback) {
        try { return Long.parseLong(s.trim().replace("_","")); }
        catch (Exception e) { return fallback; }
    }
}
