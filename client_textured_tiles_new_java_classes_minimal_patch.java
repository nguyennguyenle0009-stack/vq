// FILE: client/src/main/java/rt/client/game/ui/tile/ImageTileRenderer.java
package rt.client.game.ui.tile;

import rt.client.model.WorldModel;
import rt.client.world.ChunkCache;
import rt.client.assets.Tileset;
import rt.common.world.ChunkPos;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renderer dùng texture (sprite tile) để vẽ world theo từng CHUNK đã ghép sẵn.
 * - Không sửa/chạm vào ChunkCache hiện tại.
 * - Nếu không tìm thấy tileset, sẽ fallback về màu phẳng (dùng ChunkCache.bakeImage).
 *
 * Sử dụng:
 *   ImageTileRenderer r = new ImageTileRenderer();
 *   r.setChunkCache(cache);
 *   r.setTileSize(TILE);
 *   // optional: r.setTileset(new Tileset("/tiles/terrain.png", 32, 32));
 *   r.draw(g2, model);
 */
public class ImageTileRenderer {

    /** Cache ảnh đã ghép theo đơn vị CHUNK (mỗi key = cx,cy + tileSize + tilesetRev). */
    private final Map<Key, BufferedImage> chunkImageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                private static final int MAX = 512; // giống ChunkCache
                @Override protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> e) {
                    return size() > MAX;
                }
            });

    private static record Key(int cx, int cy, int tileSize, int tilesetRev) {}

    private ChunkCache chunkCache;
    private int tileSize = 32;
    private Tileset tileset;          // null -> tự tạo mặc định; not loaded -> fallback

    public void setChunkCache(ChunkCache cc){ this.chunkCache = cc; }
    public void setTileSize(int px){ this.tileSize = px; }
    public void setTileset(Tileset ts){ this.tileset = ts; }

    public void draw(Graphics2D g2, WorldModel model){
        if (chunkCache == null || model == null) return;

        final Rectangle clip = g2.getClipBounds();
        if (clip == null) return;

        final int TILE = this.tileSize;
        final int N = ChunkPos.SIZE;
        final int Npx = N * TILE;

        // clamp theo chunk bounds
        int cx0 = Math.floorDiv(clip.x, Npx);
        int cy0 = Math.floorDiv(clip.y, Npx);
        int cx1 = Math.floorDiv(clip.x + clip.width  - 1, Npx);
        int cy1 = Math.floorDiv(clip.y + clip.height - 1, Npx);

        // xác định revision tileset hiện tại để cache invalidation an toàn
        final int rev = (tileset != null) ? tileset.revision() : 0;

        for (int cy = cy0; cy <= cy1; cy++){
            for (int cx = cx0; cx <= cx1; cx++){
                ChunkCache.Data d = chunkCache.get(cx, cy);
                if (d == null) continue;

                Key key = new Key(cx, cy, TILE, rev);
                BufferedImage img = chunkImageCache.get(key);

                if (img == null) {
                    img = buildChunkImage(d, TILE);
                    if (img != null) chunkImageCache.put(key, img);
                }

                if (img != null) {
                    int dx = cx * Npx;
                    int dy = cy * Npx;
                    g2.drawImage(img, dx, dy, null);
                }
            }
        }
    }

    /** Ghép ảnh cho 1 chunk: nếu tileset khả dụng -> vẽ sprite; ngược lại fallback màu phẳng. */
    private BufferedImage buildChunkImage(ChunkCache.Data d, int tileSize){
        // Fallback khi không có/không load được tileset
        if (tileset == null || !tileset.isReady()){
            // dùng sẵn logic cũ để đảm bảo tương thích
            chunkCache.bakeImage(d, tileSize);
            return d.img;
        }

        final int N = d.size; // ChunkPos.SIZE
        final int W = N * tileSize;
        BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int ty = 0; ty < N; ty++){
            for (int tx = 0; tx < N; tx++){
                int id = d.l1[ty * N + tx] & 0xFF; // layer1 chính
                Image tile = tileset.getById(id);
                int x = tx * tileSize;
                int y = ty * tileSize;

                if (tile != null){
                    g.drawImage(tile, x, y, tileSize, tileSize, null);
                } else {
                    // nếu id chưa map sprite -> vẽ màu phẳng để không bị trống
                    int argb = rt.common.world.TerrainPalette.color(id);
                    g.setColor(new Color(argb, true));
                    g.fillRect(x, y, tileSize, tileSize);
                }
            }
        }
        g.dispose();
        return img;
    }
}

// FILE: client/src/main/java/rt/client/assets/SpriteSheet.java
package rt.client.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Helper cắt sprite-sheet dạng lưới đều nhau. */
public final class SpriteSheet {
    public final BufferedImage sheet;
    public final int tileW;
    public final int tileH;
    public final int cols;
    public final int rows;

    public SpriteSheet(BufferedImage sheet, int tileW, int tileH) {
        this.sheet = sheet;
        this.tileW = tileW;
        this.tileH = tileH;
        this.cols = sheet.getWidth() / tileW;
        this.rows = sheet.getHeight() / tileH;
    }

    public static SpriteSheet load(String resourcePath, int tileW, int tileH) {
        try (InputStream in = SpriteSheet.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            return new SpriteSheet(img, tileW, tileH);
        } catch (IOException e) {
            return null;
        }
    }

    public BufferedImage subImage(int col, int row){
        int x = col * tileW;
        int y = row * tileH;
        return sheet.getSubimage(x, y, tileW, tileH);
    }
}

// FILE: client/src/main/java/rt/client/assets/Tileset.java
package rt.client.assets;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Map terrain-id -> sprite từ 1 sprite-sheet.
 * Mặc định: id = index theo hàng (row-major), ví dụ id 0 = (0,0), id 1 = (1,0)...
 * Có thể chỉnh map thủ công qua put(id, col, row).
 */
public final class Tileset {
    private final SpriteSheet sheet;
    private final Map<Integer, Image> cache = new HashMap<>();
    private int revision = 1;

    public Tileset(SpriteSheet sheet) {
        this.sheet = sheet;
    }

    /** Tạo nhanh: đọc từ resourcePath. Nếu fail -> trả Tileset nullReady. */
    public static Tileset from(String resourcePath, int tileW, int tileH){
        SpriteSheet s = SpriteSheet.load(resourcePath, tileW, tileH);
        if (s == null) return new Tileset(null);
        return new Tileset(s);
    }

    public boolean isReady(){ return sheet != null; }
    public int revision(){ return revision; }

    /** id -> Image, theo quy ước id = row*cols + col */
    public Image getById(int id){
        if (!isReady()) return null;
        Image img = cache.get(id);
        if (img != null) return img;

        int cols = sheet.cols;
        int row = id / cols;
        int col = id % cols;

        if (row >= sheet.rows) return null;
        BufferedImage sub = sheet.subImage(col, row);
        cache.put(id, sub);
        return sub;
    }

    /** Gán thủ công (ví dụ khi id không khớp index). Gọi khi cần -> sẽ bump revision để cache ảnh chunk invalid. */
    public void put(int id, int col, int row){
        if (!isReady()) return;
        if (row >= sheet.rows || col >= sheet.cols) return;
        cache.put(id, sheet.subImage(col, row));
        revision++;
    }
}

// --- MINIMAL PATCH: client/src/main/java/rt/client/game/ui/GameCanvas.java ---
// Thay import:
//   import rt.client.game.ui.tile.TileRenderer;
// bằng:
//   import rt.client.game.ui.tile.ImageTileRenderer;
//   import rt.client.assets.Tileset;
//
// Thay field:
//   private final TileRenderer tileRenderer = new TileRenderer();
// bằng:
//   private final ImageTileRenderer tileRenderer = new ImageTileRenderer();
//
// Trong constructor (hoặc init), sau khi set chunkCache & TILE cho renderer cũ, thêm:
//   tileRenderer.setChunkCache(chunkCache);
//   tileRenderer.setTileSize(TILE);
//   // Tự động đọc spritesheet nếu bạn đặt tại: client/src/main/resources/tiles/terrain.png
//   tileRenderer.setTileset(Tileset.from("/tiles/terrain.png", 32, 32));
//
// Không cần đổi lời gọi:
//   tileRenderer.draw(g2, model);
// ImageTileRenderer sẽ tự fallback về vẽ màu phẳng nếu không có spritesheet.


// FILE: client/src/main/java/rt/client/assets/TileColor.java
package rt.client.assets;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Tạo màu đại diện cho mỗi tile-id sao cho nhất quán giữa Minimap và Bản đồ (M).
 * - Ưu tiên trích màu chủ đạo từ sprite (nếu có) để “đồng bộ thị giác”.
 * - Fallback về bảng màu TerrainPalette (common) nếu không có sprite.
 */
public final class TileColor {
    private TileColor() {}

    /** Lấy ARGB cho id, ưu tiên màu từ sprite trong Tileset. */
    public static int colorOf(int id, Tileset tileset){
        if (tileset != null && tileset.isReady()){
            Image im = tileset.getById(id);
            if (im instanceof BufferedImage bi){
                return dominantColor(bi);
            }
        }
        // Fallback: bảng màu logic cũ (giữ nguyên hành vi nếu thiếu sprite)
        return rt.common.world.TerrainPalette.color(id);
    }

    /** Lấy màu chủ đạo rất nhanh (median-of-means) để dùng cho minimap. */
    public static int dominantColor(BufferedImage img){
        int w = img.getWidth(), h = img.getHeight();
        long r=0,g=0,b=0,a=0; int n=0;
        // sample 5x5 lưới để giảm cost
        for (int yy=0; yy<5; yy++){
            for (int xx=0; xx<5; xx++){
                int x = (xx * (w-1)) / 4;
                int y = (yy * (h-1)) / 4;
                int argb = img.getRGB(x,y);
                a += (argb>>>24)&0xFF; r += (argb>>>16)&0xFF; g += (argb>>>8)&0xFF; b += argb&0xFF; n++;
            }
        }
        int A=(int)(a/n), R=(int)(r/n), G=(int)(g/n), B=(int)(b/n);
        return (A<<24)|(R<<16)|(G<<8)|B;
    }
}

// FILE: client/src/main/java/rt/client/game/ui/map/MiniMapRenderer.java
package rt.client.game.ui.map;

import rt.client.assets.TileColor;
import rt.client.assets.Tileset;
import rt.client.world.ChunkCache;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renderer minimap: vẽ theo CHUNK -> ảnh nhỏ (miniTile px mỗi ô). Không ảnh hưởng logic world.
 * Dùng chung Tileset/Palette với world để luôn **đồng bộ** màu giữa Minimap và Bản đồ (M).
 */
public final class MiniMapRenderer {
    private final Map<Key, BufferedImage> miniChunkCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true){
                private static final int MAX = 1024;
                @Override protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> e){ return size()>MAX; }
            });

    private static record Key(int cx,int cy,int miniTile,int tilesetRev){}

    private ChunkCache chunkCache;
    private Tileset tileset; // optional
    private int miniTile = 2; // px per tile trong minimap

    public void setChunkCache(ChunkCache cc){ this.chunkCache = cc; }
    public void setTileset(Tileset ts){ this.tileset = ts; }
    public void setMiniTile(int px){ this.miniTile = Math.max(1, px); }

    /**
     * Vẽ minimap trong một hình chữ nhật màn hình.
     * @param g2         Graphics2D (HUD)
     * @param dstX,Y,W,H vùng hiển thị minimap trên màn hình
     * @param centerTileX,centerTileY tâm minimap theo tọa độ TILE (thường là player)
     * @param viewTilesW,viewTilesH số tile bao trùm minimap (tính trước = W/miniTile,...)
     * @param cameraTilesRect hình chữ nhật tầm nhìn hiện tại (đơn vị TILE) để vẽ khung (có thể null)
     */
    public void draw(Graphics2D g2, int dstX, int dstY, int dstW, int dstH,
                     int centerTileX, int centerTileY, int viewTilesW, int viewTilesH,
                     Rectangle cameraTilesRect, Color cameraRectColor){
        if (chunkCache == null || dstW<=0 || dstH<=0) return;
        final int mt = miniTile;
        // Tính tile-range cần vẽ quanh tâm
        int halfW = viewTilesW/2;
        int halfH = viewTilesH/2;
        int t0x = centerTileX - halfW;
        int t0y = centerTileY - halfH;

        // Vẽ nền (alpha mờ)
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));
        g2.setColor(new Color(0,0,0,160));
        g2.fillRect(dstX, dstY, dstW, dstH);
        g2.setComposite(old);

        // Tính chunk-range
        final int N = rt.common.world.ChunkPos.SIZE;
        int cx0 = floorDiv(t0x, N);
        int cy0 = floorDiv(t0y, N);
        int cx1 = floorDiv(t0x + viewTilesW - 1, N);
        int cy1 = floorDiv(t0y + viewTilesH - 1, N);
        final int tilesetRev = (tileset!=null)? tileset.revision():0;

        for (int cy = cy0; cy <= cy1; cy++){
            for (int cx = cx0; cx <= cx1; cx++){
                ChunkCache.Data d = chunkCache.get(cx, cy);
                if (d == null) continue;
                Key key = new Key(cx, cy, mt, tilesetRev);
                BufferedImage img = miniChunkCache.get(key);
                if (img == null){
                    img = buildMiniChunkImage(d, mt);
                    if (img!=null) miniChunkCache.put(key, img);
                }
                if (img == null) continue;

                // Vị trí chunk trong minimap theo tile offset
                int chunkTileX = cx * N;
                int chunkTileY = cy * N;
                int sx = (chunkTileX - t0x) * mt;
                int sy = (chunkTileY - t0y) * mt;
                int sw = d.size * mt;
                int sh = sw;

                // Cắt nếu vượt dst
                Shape oldClip = g2.getClip();
                g2.setClip(new Rectangle(dstX, dstY, dstW, dstH));
                g2.drawImage(img, dstX + sx, dstY + sy, sw, sh, null);
                g2.setClip(oldClip);
            }
        }

        // Vẽ khung camera (nếu có) để đồng bộ với map M
        if (cameraTilesRect != null){
            int rx = dstX + (cameraTilesRect.x - t0x) * mt;
            int ry = dstY + (cameraTilesRect.y - t0y) * mt;
            int rw = cameraTilesRect.width * mt;
            int rh = cameraTilesRect.height * mt;
            g2.setColor(cameraRectColor != null ? cameraRectColor : Color.WHITE);
            g2.drawRect(rx, ry, Math.max(1,rw), Math.max(1,rh));
        }
    }

    /** ghép ảnh minimap cho 1 chunk (mỗi tile = miniTile px, dùng màu đại diện) */
    private BufferedImage buildMiniChunkImage(ChunkCache.Data d, int mt){
        final int N = d.size;
        BufferedImage img = new BufferedImage(N*mt, N*mt, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int ty=0; ty<N; ty++){
            for (int tx=0; tx<N; tx++){
                int id = d.l1[ty*N + tx] & 0xFF;
                int argb = TileColor.colorOf(id, tileset);
                g.setColor(new Color(argb, true));
                g.fillRect(tx*mt, ty*mt, mt, mt);
            }
        }
        g.dispose();
        return img;
    }

    private static int floorDiv(int a, int b){
        int q = a / b; int r = a % b; return (r!=0 && ((a^b)<0)) ? (q-1) : q;
    }
}

// FILE: client/src/main/java/rt/client/game/ui/map/FullMapRenderer.java
package rt.client.game.ui.map;

import rt.client.world.ChunkCache;
import rt.client.assets.Tileset;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Renderer cho BẢN ĐỒ TOÀN MÀN HÌNH (M):
 * - Tận dụng MiniMapRenderer nhưng cho phép scale để fit màn hình.
 * - Vẫn đồng bộ màu/spriteset thông qua Tileset.
 */
public final class FullMapRenderer {
    private final MiniMapRenderer mini = new MiniMapRenderer();

    public FullMapRenderer setChunkCache(ChunkCache cc){ mini.setChunkCache(cc); return this; }
    public FullMapRenderer setTileset(Tileset ts){ mini.setTileset(ts); return this; }
    public FullMapRenderer setMiniTile(int px){ mini.setMiniTile(px); return this; }

    /**
     * Vẽ full-map quét vùng tile từ (tileX0,tileY0) kích thước (tilesW x tilesH) vào dstRect.
     * Truyền cameraTilesRect để vẽ khung tương ứng tầm nhìn hiện tại.
     */
    public void draw(Graphics2D g2, Rectangle dstRect,
                     int tileX0, int tileY0, int tilesW, int tilesH,
                     Rectangle cameraTilesRect){
        // Vẽ vào offscreen = tilesW*mt x tilesH*mt, rồi scale fill vào dstRect để tránh méo ô lưới.
        int mt = Math.max(1, tilesToMiniScale(dstRect, tilesW, tilesH));
        BufferedImage off = new BufferedImage(tilesW*mt, tilesH*mt, BufferedImage.TYPE_INT_ARGB);
        Graphics2D og = off.createGraphics();
        mini.draw(og, 0, 0, off.getWidth(), off.getHeight(),
                tileX0 + tilesW/2, tileY0 + tilesH/2, tilesW, tilesH,
                (cameraTilesRect==null? null : new Rectangle(cameraTilesRect)), Color.WHITE);
        og.dispose();

        g2.drawImage(off, dstRect.x, dstRect.y, dstRect.width, dstRect.height, null);
    }

    private int tilesToMiniScale(Rectangle dst, int tilesW, int tilesH){
        int sx = Math.max(1, dst.width / Math.max(1, tilesW));
        int sy = Math.max(1, dst.height / Math.max(1, tilesH));
        return Math.max(1, Math.min(sx, sy));
    }
}

// --- MINIMAL PATCH – HUD minimap (ví dụ HudOverlay) ---
// 1) Tạo 1 instance dùng chung Tileset với world renderer
//    private final MiniMapRenderer mini = new MiniMapRenderer();
//    // ở init:
//    mini.setChunkCache(chunkCache);
//    mini.setTileset(sharedTileset); // chính là Tileset bạn đã set cho ImageTileRenderer
//    mini.setMiniTile(2);            // hoặc 3/4 tùy kích cỡ ô minimap
//
// 2) Trong phương thức vẽ HUD minimap:
//    int dstX = screenW - 10 - 200; // ví dụ góc phải
//    int dstY = 10;
//    int dstW = 200, dstH = 200;
//    int viewTilesW = dstW / 2;      // nếu miniTile=2
//    int viewTilesH = dstH / 2;
//    // Lấy tâm theo tile (player)
//    int playerTileX = (int)Math.floor(model.playerX / TILE);
//    int playerTileY = (int)Math.floor(model.playerY / TILE);
//    // Khung camera theo đơn vị TILE
//    int camW = viewWidthPx  / TILE;
//    int camH = viewHeightPx / TILE;
//    int camX = (int)Math.floor(cameraWorldX / TILE);
//    int camY = (int)Math.floor(cameraWorldY / TILE);
//    java.awt.Rectangle camRect = new java.awt.Rectangle(camX, camY, camW, camH);
//
//    mini.draw(g2, dstX, dstY, dstW, dstH, playerTileX, playerTileY, viewTilesW, viewTilesH, camRect, new java.awt.Color(255,255,255,200));
//
// --- MINIMAL PATCH – Bản đồ toàn màn hình (phím M) ---
// 1) Tạo 1 instance dùng chung Tileset
//    private final FullMapRenderer fullMap = new FullMapRenderer();
//    // init:
//    fullMap.setChunkCache(chunkCache).setTileset(sharedTileset).setMiniTile(2);
//
// 2) Khi bật overlay M:
//    java.awt.Rectangle screen = new java.awt.Rectangle(0,0,screenW,screenH);
//    // Xác định vùng tile muốn hiển thị. Nếu game đã có logic pan/zoom cho bản đồ M, dựa theo đó tính tileX0,Y0, tilesW,H.
//    // Ở mức tối thiểu, bạn có thể hiển thị quanh player, nhiều hơn minimap:
//    int tilesW = (screenW / 2); // tỉ lệ tùy ý
//    int tilesH = (screenH / 2);
//    int tileX0 = playerTileX - tilesW/2;
//    int tileY0 = playerTileY - tilesH/2;
//    java.awt.Rectangle camRect2 = new java.awt.Rectangle(camX, camY, camW, camH);
//    fullMap.draw(g2, screen, tileX0, tileY0, tilesW, tilesH, camRect2);


// FILE: client/src/main/java/rt/client/assets/SpriteColors.java
package rt.client.assets;

/**
 * CLIENT-ONLY: thay thế màu phẳng bằng màu lấy từ ảnh sprite.
 * - Gọi SpriteColors.init(sharedTileset) một lần ở client (vd. GameCanvas/ClientApp).
 * - Ở mọi nơi client đang dùng TerrainPalette.color(id), đổi thành SpriteColors.colorOf(id).
 * - Nếu chưa init hoặc id chưa có ảnh -> fallback TerrainPalette.color(id) (an toàn, không crash).
 */
public final class SpriteColors {
    private static Tileset tileset; // shared
    private SpriteColors() {}

    public static void init(Tileset ts){ tileset = ts; }

    public static int colorOf(int id){
        if (tileset != null && tileset.isReady()){
            java.awt.Image im = tileset.getById(id);
            if (im instanceof java.awt.image.BufferedImage bi){
                return TileColor.dominantColor(bi);
            }
        }
        return rt.common.world.TerrainPalette.color(id); // fallback giữ nguyên hành vi cũ
    }
}

// FILE: client/src/main/java/rt/client/assets/TilesetDir.java
package rt.client.assets;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nạp ảnh từ THƯ MỤC thay vì sprite-sheet (phù hợp khi bạn có nhiều file lẻ trong world.zip).
 * Quy ước:
 *  - (A) Dùng map properties: "/img/terrain/map.properties" chứa dòng "<id>=<file.png>".
 *  - (B) Nếu không có properties, tự dò theo tên: "/img/terrain/<id>.png" (vd. 0.png, 2.png,...)
 * Cho phép đồng thời tồn tại với Tileset (sprite-sheet). Bạn có thể wrap các ảnh thư mục vào TilesetDir,
 * rồi cung cấp cho ImageTileRenderer & SpriteColors.
 */
public final class TilesetDir extends Tileset {
    private final Map<Integer, Image> byId = new HashMap<>();
    private int rev = 1;

    private TilesetDir(){ super(null); }

    public static TilesetDir fromDir(String base){
        TilesetDir ts = new TilesetDir();
        // (A) map.properties nếu có
        Properties p = new Properties();
        try (InputStream in = TilesetDir.class.getResourceAsStream(base + "/map.properties")){
            if (in != null) p.load(in);
        } catch (IOException ignored) {}
        if (!p.isEmpty()){
            for (String k : p.stringPropertyNames()){
                try {
                    int id = Integer.parseInt(k.trim());
                    String file = p.getProperty(k).trim();
                    Image img = load(base + "/" + file);
                    if (img != null) ts.byId.put(id, img);
                } catch (NumberFormatException ignored) {}
            }
        }
        // (B) fallback: thử id.png cho một dải id hay dùng (0..255)
        for (int id=0; id<256; id++){
            if (ts.byId.containsKey(id)) continue;
            Image img = load(base + "/" + id + ".png");
            if (img != null) ts.byId.put(id, img);
        }
        ts.rev++;
        return ts;
    }

    private static Image load(String path){
        try (InputStream in = TilesetDir.class.getResourceAsStream(path)){
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            return img;
        } catch (IOException e){
            return null;
        }
    }

    @Override public boolean isReady(){ return true; }
    @Override public int revision(){ return rev; }
    @Override public Image getById(int id){ return byId.get(id); }
    @Override public void put(int id, int col, int row){ /* not used for dir */ }
}

// --- CLIENT-ONLY PATCH GUIDE (không đụng common) ---
// 1) Khởi tạo shared tileset từ thư mục ảnh trong world.zip:
//    Tileset sharedTileset = TilesetDir.fromDir("/img/terrain");
//    // hoặc nếu bạn có sprite-sheet: Tileset.from("/tiles/terrain.png", 32, 32)
//    SpriteColors.init(sharedTileset); // để minimap/map dùng màu từ ảnh
//
// 2) Ở World renderer (ImageTileRenderer) -> setTileset(sharedTileset)
//
// 3) Ở các renderer của MiniMap & Map (M):
//    Tìm mọi chỗ gọi: TerrainPalette.color(id)
//    Thay bằng:       SpriteColors.colorOf(id)
//    -> Không cần sửa logic nào khác, chỉ đổi hàm lấy màu.
//
// 4) Nếu minimap/map có cache ảnh theo chunk, hãy tăng "tilesetRev" (nếu có) bằng sharedTileset.revision() để auto invalid cache khi đổi ảnh.
