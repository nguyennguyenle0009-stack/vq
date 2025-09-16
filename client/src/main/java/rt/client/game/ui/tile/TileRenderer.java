package rt.client.game.ui.tile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rt.client.model.WorldModel;

public final class TileRenderer {
	private static final int[] COLLISION_SPRITES = {1, 2, 3}; // các sprite dành cho vật đè (va chạm)
	private static final float OVERLAY_SCALE = 0.75f;         // vẽ nhỏ hơn tile để lộ nền
	private static final boolean DRAW_COLLIDER_BORDER = true;  // vẽ khung cho dễ nhìn
	
	private static final String COLLIDER_IMAGE_PATH = "/tiles/overlay/collider.png";
	private java.awt.image.BufferedImage colliderImg = null;
	
    private static final Logger log = LoggerFactory.getLogger(TileRenderer.class);

    private static final String DEFAULT_TILESET = "/tiles/overworld.png";
    private static final int DEFAULT_COLS = 16;

    private BufferedImage tileset;
    private BufferedImage[] atlas;
    private int lastTileSize = -1;

    private void ensureAtlas(int tile) {
        if (tileset != null && atlas != null && lastTileSize == tile) return;
        lastTileSize = tile;
        
        if (colliderImg == null) {
            try (var in = TileRenderer.class.getResourceAsStream(COLLIDER_IMAGE_PATH)) {
                if (in != null) colliderImg = javax.imageio.ImageIO.read(in);
            } catch (Exception ignore) {}
        }

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
            // vẽ nền như bạn đang làm
            Rectangle clip = g2.getClipBounds();
            if (clip == null) clip = new Rectangle(0, 0, screenW, screenH);

            int startTx = (int)Math.floor(clip.getMinX() / T) - 1;
            int endTx   = (int)Math.ceil (clip.getMaxX() / T) + 1;
            int startTy = (int)Math.floor(clip.getMinY() / T) - 1;
            int endTy   = (int)Math.ceil (clip.getMaxY() / T) + 1;

            for (int ty = startTy; ty <= endTy; ty++)
                for (int tx = startTx; tx <= endTx; tx++)
                    g2.drawImage(floor, tx * T, ty * T, null);

            // ---- OVERLAY từ CHUNK: dùng WorldModel.isSolidTile(tx,ty) ----
            final boolean haveSprites = (atlas.length > 1) || (colliderImg != null);
            for (int ty = startTy; ty <= endTy; ty++) {
                for (int tx = startTx; tx <= endTx; tx++) {
                    if (!model.isSolidTile(tx, ty)) continue;

                    if (haveSprites) {
                        int sz = Math.max(1, Math.round(T * OVERLAY_SCALE));
                        int ox = tx * T + (T - sz) / 2;
                        int oy = ty * T + (T - sz) / 2;

                        if (colliderImg != null) {
                            g2.drawImage(colliderImg, ox, oy, sz, sz, null);
                        } else {
                            // chọn sprite từ spritesheet theo (tx,ty) để đa dạng
                            int pick = COLLISION_SPRITES[Math.floorMod((tx * 73856093) ^ (ty * 19349663), COLLISION_SPRITES.length)];
                            int idx = (pick < atlas.length) ? pick : 1;
                            Image obj = atlas[idx];
                            g2.drawImage(obj, ox, oy, sz, sz, null);
                        }
                    } else {
                        // fallback: phủ màu
                        java.awt.Composite old = g2.getComposite();
                        g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.40f));
                        g2.setColor(new java.awt.Color(235, 70, 70));
                        g2.fillRect(tx * T, ty * T, T, T);
                        g2.setComposite(old);
                    }

                    if (DRAW_COLLIDER_BORDER) {
                        java.awt.Stroke oldS = g2.getStroke();
                        g2.setStroke(new java.awt.BasicStroke(1f));
                        g2.setColor(new java.awt.Color(255, 50, 50, 130));
                        g2.drawRect(tx * T, ty * T, T - 1, T - 1);
                        g2.setStroke(oldS);
                    }
                }
            }

            // KHÔNG return ở đây nữa — nhưng để rõ ràng có thể return; 
            // vì các nhánh bên dưới đều là vẽ theo MapModel, không chạy vào nữa
            return;
        }

        // ====== Trường hợp có MapModel: vẽ đầy đủ theo map ======
        for (int y = 0; y < mm.h; y++)
            for (int x = 0; x < mm.w; x++)
                g2.drawImage(floor, x * T, y * T, null);
        
        if (wall != null) {
            for (int y = 0; y < mm.h; y++) {
                for (int x = 0; x < mm.w; x++) 
            	if (mm.solid[y][x]) {
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
        
     // ---- Overlay va chạm: chọn sprite theo tọa độ, vẽ nhỏ và canh giữa ----
        if (mm != null) {
            // nếu atlas không đủ sprite, fallback sang phủ màu
            boolean haveSprites = atlas.length > 1;

            for (int y = 0; y < mm.h; y++) {
                for (int x = 0; x < mm.w; x++) {
                    if (!mm.solid[y][x]) continue;

                    if (haveSprites) {
                        // chọn sprite theo hash(x,y) để đa dạng (ổn định theo toạ độ)
                        int pick = COLLISION_SPRITES[Math.floorMod((x * 73856093) ^ (y * 19349663), COLLISION_SPRITES.length)];
                        int idx = (pick < atlas.length) ? pick : 1;
                        Image obj = atlas[idx];

                        int sz = Math.max(1, Math.round(T * OVERLAY_SCALE));
                        int ox = x * T + (T - sz) / 2;
                        int oy = y * T + (T - sz) / 2;

                        g2.drawImage(obj, ox, oy, sz, sz, null);
                    } else {
                        // Fallback: phủ màu
                        Composite old = g2.getComposite();
                        g2.setComposite(AlphaComposite.SrcOver.derive(0.40f));
                        g2.setColor(new Color(235, 70, 70));
                        g2.fillRect(x * T, y * T, T, T);
                        g2.setComposite(old);
                    }

                    if (DRAW_COLLIDER_BORDER) {
                        Stroke oldS = g2.getStroke();
                        g2.setStroke(new BasicStroke(1f));
                        g2.setColor(new Color(255, 50, 50, 130));
                        g2.drawRect(x * T, y * T, T - 1, T - 1);
                        g2.setStroke(oldS);
                    }
                }
            }
        }
    }
}
