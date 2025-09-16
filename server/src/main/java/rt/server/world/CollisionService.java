package rt.server.world;

import rt.common.map.Grid;
import rt.common.map.TileChunk;

public final class CollisionService {
    private final WorldRegistry.WorldCtx ctx;

    public CollisionService(WorldRegistry.WorldCtx ctx) { this.ctx = ctx; }
    private static final boolean DISABLE_COLLISION = false;

    public boolean blockedTile(int tx, int ty){
        if (DISABLE_COLLISION) return false;            // ❗ tắt hết để test

        final int cx = Grid.chunkX(tx), cy = Grid.chunkY(ty);
        final TileChunk ch = ctx.chunks.get(cx, cy);    // lazy-generate
        if (ch == null) return false;                   // ❗ chunk chưa có => không chặn
        final int lx = Grid.localX(tx), ly = Grid.localY(ty);
        return ch.blocked(lx, ly);                      // ❗ chỉ object solid mới true
    }

    public boolean blockedPixel(int px, int py){
        int t = Grid.TILE;
        int tx = (int)Math.floor(px / (double)t);
        int ty = (int)Math.floor(py / (double)t);
        return blockedTile(tx, ty);
    }
}
