package rt.common.map;

import java.util.BitSet;

public final class MountainTerrain implements TerrainGeneratorEx {
    private double rough = 32;
    @Override public void configure(TerrainParams p){ rough = p.num("rough", 32); }
    private static double ridge(long seed,int x,int y,double s){
        double v = Math.sin((x+seed)%1000 / s) * Math.cos((y-seed)%1000 / s);
        return Math.abs(v);
    }
    @Override public TileChunk generate(long seed, int cx, int cy) {
        int W=Grid.CHUNK,H=Grid.CHUNK; short[] tiles=new short[W*H]; java.util.BitSet solid=new java.util.BitSet(W*H);
        for (int ly=0; ly<H; ly++) for (int lx=0; lx<W; lx++){
            int wx=cx*W+lx, wy=cy*H+ly; double h=ridge(seed, wx, wy, rough);
            boolean rock=h>0.2; int i=ly*W+lx; tiles[i]=(short)(rock?4:1); if(rock) solid.set(i);
        }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }
}
