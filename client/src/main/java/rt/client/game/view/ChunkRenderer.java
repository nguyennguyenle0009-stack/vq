package rt.client.game.view;

import rt.client.game.chunk.ChunkCache;
import rt.common.map.Grid;

import java.awt.*;

public final class ChunkRenderer {
    private final ChunkCache cache;
    private final TileAtlas atlas;

    public ChunkRenderer(ChunkCache cache, TileAtlas atlas){
        this.cache=cache; this.atlas=atlas;
    }

    public void draw(Graphics2D g2, double camX, double camY, int viewTilesW, int viewTilesH){
        int minTx = (int)Math.floor(camX - viewTilesW/2.0);
        int minTy = (int)Math.floor(camY - viewTilesH/2.0);
        int maxTx = minTx + viewTilesW;
        int maxTy = minTy + viewTilesH;

        int minCx = Grid.chunkX(minTx), maxCx = Grid.chunkX(maxTx);
        int minCy = Grid.chunkY(minTy), maxCy = Grid.chunkY(maxTy);

        for (int cy=minCy; cy<=maxCy; cy++){
            for (int cx=minCx; cx<=maxCx; cx++){
                ChunkCache.ChunkView cv = cache.get(cx,cy);
                if (cv == null) continue;
                int startTx = Math.max(minTx, cx*Grid.CHUNK);
                int endTx   = Math.min(maxTx, cx*Grid.CHUNK + Grid.CHUNK);
                int startTy = Math.max(minTy, cy*Grid.CHUNK);
                int endTy   = Math.min(maxTy, cy*Grid.CHUNK + Grid.CHUNK);
                for (int ty=startTy; ty<endTy; ty++){
                    for (int tx=startTx; tx<endTx; tx++){
                        int lx = Grid.localX(tx), ly = Grid.localY(ty);
                        int id = cv.tileAt(lx, ly);
                        if (id == 0) continue;
                        atlas.draw(g2, id, tx*Grid.TILE, ty*Grid.TILE);
                    }
                }
            }
        }
    }
}
