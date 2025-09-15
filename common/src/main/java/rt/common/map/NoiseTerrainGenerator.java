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
        int W=Grid.CHUNK,H=Grid.CHUNK; short[] tiles=new short[W*H]; java.util.BitSet solid=new java.util.BitSet(W*H);
        for (int ly=0; ly<H; ly++) for (int lx=0; lx<W; lx++){
            int worldX=cx*W+lx, worldY=cy*H+ly;
            // BASE: luôn vẽ tile 0 làm nền
            // OVERLAY (collision): thưa thớt, tile 1 và set solid
            boolean object = (Math.abs(h2(seed, worldX, worldY)) % 20)==0;
            int idx = ly*W+lx;
            tiles[idx] = (short)(object ? 1 : 0);
            if (object) solid.set(idx);
        }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }

    
    public TileChunk generateA(long seed, int cx, int cy) {
        int W=Grid.CHUNK,H=Grid.CHUNK; short[] tiles=new short[W*H]; java.util.BitSet solid=new java.util.BitSet(W*H);
        for (int ly=0; ly<H; ly++) for (int lx=0; lx<W; lx++){
            int worldX=cx*W+lx, worldY=cy*H+ly;
            int hx = 12 + Math.abs(h2(seed, worldX, 0)) % 6;
            boolean ground = (ly >= hx);
            boolean object = (Math.abs(h2(seed, worldX, worldY)) % 20)==0;
            boolean isSolid = ground || object;
            int i=ly*W+lx; tiles[i]=(short)(isSolid?1:0); if (isSolid) solid.set(i);
        }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }
}
