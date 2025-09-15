package rt.client.game.ui.tile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rt.client.model.WorldModel;

public final class TileRenderer {
	private static final int COLLISION_SPRITE_INDEX = 1;
	
    private static final Logger log = LoggerFactory.getLogger(TileRenderer.class);

    private static final String DEFAULT_TILESET = "/tiles/overworld.png";
    private static final int DEFAULT_COLS = 16;

    private BufferedImage tileset;
    private BufferedImage[] atlas;
    private int lastTileSize = -1;

    private void ensureAtlas(int tile) {
        if (tileset != null && atlas != null && lastTileSize == tile) return;
        lastTileSize = tile;

        try (InputStream in = TileRenderer.class.getResourceAsStream(DEFAULT_TILESET)) {
            if (in != null) {
                tileset = ImageIO.read(in);
                log.info("Tileset loaded: {}", DEFAULT_TILESET);
            } else {
                log.warn("Tileset NOT found on classpath: {} -> fallback colors", DEFAULT_TILESET);
                tileset = new BufferedImage(DEFAULT_COLS * tile, tile, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = tileset.createGraphics();
                for (int i = 0; i < DEFAULT_COLS; i++) {
                    gg.setColor(new Color(110 + (i * 7) % 120, 140, 160));
                    gg.fillRect(i * tile, 0, tile, tile);
                    gg.setColor(new Color(0, 0, 0, 30));
                    gg.drawRect(i * tile, 0, tile - 1, tile - 1);
                }
                gg.dispose();
            }
        } catch (Exception e) {
            log.error("Read tileset error", e);
            tileset = new BufferedImage(DEFAULT_COLS * tile, tile, BufferedImage.TYPE_INT_ARGB);
        }

        int cols = Math.max(1, tileset.getWidth() / tile);
        int rows = Math.max(1, tileset.getHeight() / tile);
        atlas = new BufferedImage[cols * rows];
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++)
                atlas[y * cols + x] = tileset.getSubimage(x * tile, y * tile, tile, tile);
    }

    /** Vẽ nền/tiles.
     *  - Nếu còn dùng MapModel: vẽ theo mm.
     *  - Nếu chưa có map/chunk: lấp đầy viewport bằng tile 0.
     */
    public void draw(Graphics2D g2, WorldModel model, int screenW, int screenH) {
        var mm = model.map();                       // có thể null
        int T = (mm != null ? mm.tile : 16);
        ensureAtlas(T);

        Image floor = atlas.length > 0 ? atlas[0] : null;
        Image wall  = atlas.length > 1 ? atlas[1] : floor;
        if (floor == null) return;

        // ====== Trường hợp chưa có map/chunk: lấp viewport ======
        if (mm == null) {
            // g2 đã được dịch theo camera tại GameCanvas
            Rectangle clip = g2.getClipBounds();
            if (clip == null) clip = new Rectangle(0, 0, screenW, screenH);

            int startTx = (int)Math.floor(clip.getMinX() / T) - 1;
            int endTx   = (int)Math.ceil (clip.getMaxX() / T) + 1;
            int startTy = (int)Math.floor(clip.getMinY() / T) - 1;
            int endTy   = (int)Math.ceil (clip.getMaxY() / T) + 1;

            for (int ty = startTy; ty <= endTy; ty++)
                for (int tx = startTx; tx <= endTx; tx++)
                    g2.drawImage(floor, tx * T, ty * T, null);

            return;
        }

        // ====== Trường hợp có MapModel: vẽ đầy đủ theo map ======
        for (int y = 0; y < mm.h; y++)
            for (int x = 0; x < mm.w; x++)
                g2.drawImage(floor, x * T, y * T, null);

        if (wall != null) {
            for (int y = 0; y < mm.h; y++)
                for (int x = 0; x < mm.w; x++)
                    if (mm.solid[y][x]) g2.drawImage(wall, x * T, y * T, null);
        }
        
        if (wall != null) {
            for (int y = 0; y < mm.h; y++) {
                for (int x = 0; x < mm.w; x++) if (mm.solid[y][x]) {
                    g2.drawImage(wall, x*T, y*T, null);
                    // vẽ khung để rõ ô va chạm
                    Stroke oldS = g2.getStroke();
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(new Color(255, 50, 50, 120));
                    g2.drawRect(x*T, y*T, T-1, T-1);
                    g2.setStroke(oldS);
                }
            }
        }
    }
}
