package rt.common.map;

import java.util.BitSet;

public final class DesertTerrain implements TerrainGeneratorEx {
    private double duneScale = 48;
    @Override public void configure(TerrainParams p){ duneScale = p.num("duneScale", 48); }
    @Override public TileChunk generate(long seed, int cx, int cy) {
        int W=Grid.CHUNK,H=Grid.CHUNK; short[] tiles=new short[W*H]; BitSet solid=new BitSet(W*H);
        for (int ly=0; ly<H; ly++) for (int lx=0; lx<W; lx++){
            int wx=cx*W+lx; double h = 12 + 3*Math.sin(wx / duneScale);
            boolean sand = (ly >= h); boolean ridge = (Math.abs(Math.sin((wx + ly + seed)*0.05)) > 0.96);
            int i=ly*W+lx; tiles[i]=(short)(sand?2:0); if (ridge) solid.set(i);
        }
        return new TileChunk(cx, cy, W, H, tiles, solid, 1);
    }
}
