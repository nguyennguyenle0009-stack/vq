package rt.client.game.ui.tile;

import rt.common.world.ChunkPos;
import java.awt.*;

public class TileRenderer {
    private rt.client.world.ChunkCache chunkCache;
    private int tileSize = 32;

    public void setChunkCache(rt.client.world.ChunkCache cc){ this.chunkCache = cc; }
    public void setTileSize(int px){ this.tileSize = px; }

    private static final Color[] PALETTE = {
        new Color(0x1B5E9C), Color.BLACK,
        new Color(0xC8E6C9), new Color(0x2E7D32),
        new Color(0xD7CCC8), new Color(0x6D4C41)
    };

    /** Vẽ theo camera hiện tại (đã translate ở GameCanvas). Không resample, không recentre. */
    public void draw(Graphics2D g2, rt.client.model.WorldModel model,
                     int viewW, int viewH, double camX, double camY) {
        if (chunkCache == null) return;

        final int N = ChunkPos.SIZE;
        final int Npx = N * tileSize;

        // chunk chứa tâm camera
        int cx = (int)Math.floor(camX / Npx);
        int cy = (int)Math.floor(camY / Npx);

        // cắt theo viewport + 1 chunk biên
        int halfW = (int)Math.ceil(viewW / (double)Npx) + 1;
        int halfH = (int)Math.ceil(viewH / (double)Npx) + 1;

        // fillRect +1 để tránh đường seam đen giữa ô
        for (int dy = -halfH; dy <= halfH; dy++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                var d = chunkCache.get(cx + dx, cy + dy);
                if (d == null) continue;

                int baseX = (cx + dx) * Npx;
                int baseY = (cy + dy) * Npx;

                for (int ty = 0; ty < d.size; ty++) {
                    for (int tx = 0; tx < d.size; tx++) {
                        int idx = ty * d.size + tx;
                        int id  = Byte.toUnsignedInt(d.l1[idx]); // <- dùng trường l1 của bạn
                        Color c = (id < PALETTE.length) ? PALETTE[id] : Color.MAGENTA;
                        g2.setColor(c);
                        int x = baseX + tx * tileSize;
                        int y = baseY + ty * tileSize;
                        g2.fillRect(x, y, tileSize + 1, tileSize + 1);
                    }
                }
            }
        }
    }
}
