package rt.common.map;

import java.util.BitSet;

public final class TileChunk {
    public final int cx, cy, w, h;
    public final short[] tiles;
    public final BitSet solid;
    public final int version;
    public TileChunk(int cx,int cy,int w,int h, short[] tiles, BitSet solid, int version){
        this.cx=cx; this.cy=cy; this.w=w; this.h=h; this.tiles=tiles; this.solid=solid; this.version=version;
    }
    public boolean blocked(int lx,int ly){ return solid.get(ly*w + lx); }
}
