package rt.tools;

import rt.common.world.*;
import java.util.*;
import java.security.MessageDigest;

public class MapAsciiPreview {
    private static final int W = 180, H = 90; // cửa sổ xem nhanh (tile)

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 123456789L;
        double pr = 0.50, fr = 0.35; // phần còn lại là sa mạc
        WorldGenConfig cfg = new WorldGenConfig(seed, pr, fr);
        WorldGenerator gen = new WorldGenerator(cfg);

        final int N = ChunkPos.SIZE;
        byte[][] grid = new byte[H][W];

        for (int gy = 0; gy < H; gy++) {
            int y = gy - H/2;
            for (int gx = 0; gx < W; gx++) {
                int x = gx - W/2;
                int cx = Math.floorDiv(x, N), cy = Math.floorDiv(y, N);
                int tx = Math.floorMod(x, N), ty = Math.floorMod(y, N);
                ChunkData cd = gen.generate(cx, cy);
                grid[gy][gx] = cd.layer1[ty*N + tx];
            }
        }

        int[] count = new int[256];
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) count[grid[y][x] & 0xff]++;

        char[] lut = new char[256];
        lut[Terrain.OCEAN.id]    = '~';
        lut[Terrain.PLAIN.id]    = '.';
        lut[Terrain.FOREST.id]   = '♣';
        lut[Terrain.DESERT.id]   = ':';
        lut[Terrain.MOUNTAIN.id] = '^';

        StringBuilder sb = new StringBuilder();
        for (int y=0;y<H;y++){
            for (int x=0;x<W;x++) {
                int id = grid[y][x] & 0xff;
                char c = lut[id] != 0 ? lut[id] : '?';
                sb.append(c);
            }
            sb.append('\n');
        }
        System.out.print(sb);

        int total = W*H;
        System.out.printf("seed=%d, area=%d tiles\n", seed, total);
        System.out.printf("OCEAN   %6.2f%%\n", 100.0*count[Terrain.OCEAN.id]/total);
        System.out.printf("PLAIN   %6.2f%%\n", 100.0*count[Terrain.PLAIN.id]/total);
        System.out.printf("FOREST  %6.2f%%\n", 100.0*count[Terrain.FOREST.id]/total);
        System.out.printf("DESERT  %6.2f%%\n", 100.0*count[Terrain.DESERT.id]/total);
        System.out.printf("MOUNTAIN%6.2f%%\n", 100.0*count[Terrain.MOUNTAIN.id]/total);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int y=0;y<H;y++) md.update(grid[y]);
        System.out.println("hash=" + bytesToHex(md.digest()));
    }

    private static String bytesToHex(byte[] b){
        StringBuilder sb = new StringBuilder(b.length*2);
        for (byte v : b){
            int x = v & 0xff;
            sb.append("0123456789abcdef".charAt((x>>4)&0xf));
            sb.append("0123456789abcdef".charAt(x&0xf));
        }
        return sb.toString();
    }
}
