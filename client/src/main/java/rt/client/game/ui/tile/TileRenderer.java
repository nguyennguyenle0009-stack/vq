package rt.client.game.ui.tile;

import rt.client.world.ChunkBaker;
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
    public void draw(Graphics2D g2, rt.client.model.WorldModel model){
        if (chunkCache == null || model == null || model.you() == null) return;

        // vị trí bạn (pixel thế giới)
        rt.client.model.WorldModel.Pos p = model.getPredictedYou();
        if (p == null) { var s = model.sampleForRender(); p = s.get(model.you()); if (p == null) return; }
        int px = (int)Math.round(p.x * tileSize);
        int py = (int)Math.round(p.y * tileSize);

        // viewport (đã translate camera ở GameCanvas rồi -> gốc (0,0) là pixel thế giới)
        java.awt.Rectangle clip = g2.getClipBounds();
        int vw = (clip!=null? clip.width  : 2000);
        int vh = (clip!=null? clip.height : 1200);

        final int N      = rt.common.world.ChunkPos.SIZE;
        final int Npx    = N * tileSize;
        int cx0 = Math.floorDiv(px - vw/2, Npx) - 1;
        int cy0 = Math.floorDiv(py - vh/2, Npx) - 1;
        int cx1 = Math.floorDiv(px + vw/2, Npx) + 1;
        int cy1 = Math.floorDiv(py + vh/2, Npx) + 1;

        for (int cy=cy0; cy<=cy1; cy++){
            for (int cx=cx0; cx<=cx1; cx++){
                var d = chunkCache.get(cx, cy);
                if (d == null) continue;
//                chunkCache.bakeImage(d, tileSize);               // đảm bảo có ảnh
                int dx = cx * Npx;
                int dy = cy * Npx;
                //ChunkBaker.bake(d, tileSize);
                java.awt.image.BufferedImage img = ChunkBaker.getImage(d, tileSize);
                if (img != null) g2.drawImage(img, dx, dy, null);
                g2.drawImage(img, dx, dy, null);               // BLIT 1 phát
            }
        }
    }

}
