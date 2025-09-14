package rt.client.game.chunk;

import rt.common.map.Grid;
import rt.common.net.dto.ChunkS2C;
import rt.common.map.codec.BitsetRLE;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkCache {

    public static final class ChunkView {
        public final int cx, cy, w, h;
        public final short[] tiles;
        public final BitSet solid;
        public ChunkView(int cx,int cy,int w,int h, short[] tiles, BitSet solid){
            this.cx=cx; this.cy=cy; this.w=w; this.h=h; this.tiles=tiles; this.solid=solid;
        }
        public int tileAt(int lx,int ly){ return tiles[ly*w + lx]; }
        public boolean blocked(int lx,int ly){ return solid.get(ly*w + lx); }
    }

    private final ConcurrentHashMap<Long, ChunkView> map = new ConcurrentHashMap<>();
    private static long key(int cx,int cy){ return (((long)cx)<<32) ^ (cy & 0xffffffffL); }

    public void put(ChunkS2C m){
        BitSet solid = BitsetRLE.decode(m.solidRLE(), m.w()*m.h());
        map.put(key(m.cx(), m.cy()), new ChunkView(m.cx(), m.cy(), m.w(), m.h(), m.tiles(), solid));
    }

    public ChunkView get(int cx,int cy){ return map.get(key(cx,cy)); }

    public boolean blockedWorldTile(int tx,int ty){
        int cx = Grid.chunkX(tx), cy = Grid.chunkY(ty);
        int lx = Grid.localX(tx), ly = Grid.localY(ty);
        ChunkView cv = get(cx,cy);
        if (cv == null) return true;
        return cv.blocked(lx, ly);
    }
}
