package rt.server.world;

import rt.common.map.Grid;

public final class CollisionService {
    private final WorldRegistry.WorldCtx ctx;

    public CollisionService(WorldRegistry.WorldCtx ctx) { this.ctx = ctx; }

    public boolean blockedTile(int tx, int ty){
        int cx = Grid.chunkX(tx), cy = Grid.chunkY(ty);
        int lx = Grid.localX(tx), ly = Grid.localY(ty);
        var ch = ctx.chunks.get(cx, cy);
        return ch.blocked(lx, ly);
    }

    public boolean blockedPixel(int px, int py){
        int t = Grid.TILE;
        int tx = (int)Math.floor(px / (double)t);
        int ty = (int)Math.floor(py / (double)t);
        return blockedTile(tx, ty);
    }
}
