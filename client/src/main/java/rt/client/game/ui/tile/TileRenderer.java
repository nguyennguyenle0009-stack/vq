package rt.client.game.ui.tile;

import java.awt.Graphics2D;

import rt.client.model.WorldModel;
import rt.client.world.BiomePalette;
import rt.client.world.ChunkCache;
import rt.common.world.ChunkPos;

public final class TileRenderer {
    private ChunkCache chunkCache;
    private int tileSize = 32;

    public void setChunkCache(ChunkCache cc){ this.chunkCache = cc; }
    public void setTileSize(int px){ this.tileSize = px; }

    public void draw(Graphics2D g2, WorldModel model) {
        if (chunkCache == null || model == null) return;

        // LẤY VỊ TRÍ ĐÚNG CÁCH: youPos() trả WorldModel.Pos (x, y)
        int px = (int)Math.round(model.youX() * tileSize);
        int py = (int)Math.round(model.youY() * tileSize);

        int Npx = ChunkPos.SIZE * tileSize;
        int centerCx = Math.floorDiv(px, Npx);
        int centerCy = Math.floorDiv(py, Npx);

        for (int dy = -ChunkCache.R; dy <= ChunkCache.R; dy++) {
            for (int dx = -ChunkCache.R; dx <= ChunkCache.R; dx++) {
                var d = chunkCache.get(centerCx + dx, centerCy + dy);
                if (d == null) continue;

                int baseX = (centerCx + dx) * d.size * tileSize;
                int baseY = (centerCy + dy) * d.size * tileSize;

                for (int ty = 0; ty < d.size; ty++) {
                    for (int tx = 0; tx < d.size; tx++) {
                        int idx = ty * d.size + tx;
                        g2.setColor(BiomePalette.color(d.l1[idx]));
                        g2.fillRect(baseX + tx * tileSize, baseY + ty * tileSize, tileSize, tileSize);
                    }
                }
            }
        }
    }
}
