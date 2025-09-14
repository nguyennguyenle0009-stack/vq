package rt.common.map;

import java.util.BitSet;

public final class IslandsTerrain implements TerrainGeneratorEx {
    private double scale = 64;
    @Override public void configure(TerrainParams p){ scale = p.num("scale", 64); }
    private static double radial(long seed,int x,int y,double s){
        double r = Math.hypot(x/s, y/s);
        return 1.0 - r + 0.1*Math.sin((x+y+seed)%97);
    }
    @Override public TileChunk generate(long seed, int cx, int cy) {
        int W=Grid.CHUNK,H=Grid.CHUNK; short[] tiles=new short[W*H]; java.util.BitSet solid=new java.util.BitSet(W*H);
        for (int ly=0; ly<H; ly++) for (int lx=0; lx<W; lx++){
            int wx=cx*W+lx, wy=cy*H+ly; double v = radial(seed, wx, wy, scale);
            boolean land = v>0.0; int i=ly*W+lx; tiles[i]=(short)(land?3:0); if(!land) solid.set(i);
        }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }
}
