package rt.common.map;

import java.util.BitSet;

public final class NoiseTerrainGenerator implements TerrainGenerator {

    private static int h2(long seed, int x, int y){
        long z = seed;
        z ^= (x * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z ^= (y * 0x94D049BB133111EBL);
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        return (int) z;
    }

    @Override
    public TileChunk generate(long seed, int cx, int cy) {
        final int W = Grid.CHUNK, H = Grid.CHUNK;
        short[] tiles = new short[W*H];
        java.util.BitSet solid = new java.util.BitSet(W*H);

        final int[] OVERLAY_POOL = {1,2,3}; // sprite cho object

        for (int ly=0; ly<H; ly++)
            for (int lx=0; lx<W; lx++) {
                int wx = cx*W + lx, wy = cy*H + ly;
                boolean object = (Math.abs(h2(seed, wx, wy)) % 20) == 0; // rải thưa
                int idx = ly*W + lx;

                if (object) {
                    int k = Math.floorMod(h2(seed, wx, wy), OVERLAY_POOL.length);
                    tiles[idx] = (short) OVERLAY_POOL[k]; // sprite object
                    solid.set(idx);                       // ❗ chỉ object mới solid
                } else {
                    tiles[idx] = 0;                       // nền
                }
            }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }

}
