// rt/client/world/map/MapRenderer.java
package rt.client.world.map;

import rt.client.world.ChunkBaker;
import rt.client.world.ChunkCache;
import rt.common.world.ChunkPos;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.IntSupplier;

public final class MapRenderer {

    private static ChunkCache STATIC_CACHE;
    public static void setCache(ChunkCache cache) { STATIC_CACHE = cache; }

    // >>> thêm: cung cấp tileSize chính của game (vd net::tileSize)
    private static IntSupplier PRIMARY_TILE_SIZE = () -> 32;
    public static void setPrimaryTileSizeSupplier(IntSupplier s){ PRIMARY_TILE_SIZE = (s!=null? s: ()->32); }

    private final int N = ChunkPos.SIZE;

    public MapRenderer(rt.common.world.WorldGenConfig cfg) {}

    private ChunkCache getCache() { return STATIC_CACHE; }

    public BufferedImage render(long originX, long originY, double tpp, int w, int h) {
        ChunkCache cache = getCache();
        if (cache == null || w <= 0 || h <= 0) {
            return new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0x121314));
        g.fillRect(0, 0, w, h);

        // vẽ ở hệ tọa độ tile-pixel rồi scale bằng tpp
        final int gameTileSize = Math.max(8, PRIMARY_TILE_SIZE.getAsInt());
        AffineTransform bak = g.getTransform();
        g.scale(1.0 / (tpp * gameTileSize), 1.0 / (tpp * gameTileSize));

        long visTilesW = Math.round(w * tpp);
        long visTilesH = Math.round(h * tpp);

        int cx0 = Math.floorDiv((int)originX, N) - 1;
        int cy0 = Math.floorDiv((int)originY, N) - 1;
        int cx1 = Math.floorDiv((int)(originX + visTilesW), N) + 1;
        int cy1 = Math.floorDiv((int)(originY + visTilesH), N) + 1;

        for (int cy = cy0; cy <= cy1; cy++) {
          for (int cx = cx0; cx <= cx1; cx++) {
            var d = cache.get(cx, cy);
            if (d == null) continue;

         // Nếu khung (M) thấy quá nhiều chunk ⇒ hạ kích cỡ bake để an toàn
            int chunksX = cx1 - cx0 + 1, chunksY = cy1 - cy0 + 1;
            int chunksCount = chunksX * chunksY;
            int bakeTs = 16;
            if (chunksCount > 225) bakeTs = 8;
            if (chunksCount > 400) bakeTs = 4;

            // ❶ lấy ảnh TẠM (không cache)
            BufferedImage img = ChunkBaker.getImageTemp(d, bakeTs);

            // ❷ toạ độ đích theo “pixel thế giới”
            long chunkTileX = (long) cx * N, chunkTileY = (long) cy * N;
            int dx = (int) ((chunkTileX - originX) * (long)gameTileSize);
            int dy = (int) ((chunkTileY - originY) * (long)gameTileSize);
            int destW = d.size * gameTileSize;
            int destH = d.size * gameTileSize;

            // ❸ vẽ SCALE: nguồn (bakeTs) → đích (gameTileSize)
            g.drawImage(img,
                dx, dy, dx + destW, dy + destH,        // dest rect
                0, 0, img.getWidth(), img.getHeight(), // src rect
                null);
          }
        }
        g.setTransform(bak);
        g.dispose();
        return out;
    }
}
