package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.render.EntityRenderer;
import rt.client.game.ui.render.GridRenderer;
import rt.client.game.ui.tile.TileRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

/** Canvas vẽ mượt, tách riêng renderers: grid / tile / entity / HUD text. */
public class GameCanvas extends JPanel {
    public static final int TILE = 32;

    private final WorldModel model;

    // Renderers tách riêng
    private final GridRenderer gridRenderer = new GridRenderer();
    private final TileRenderer tileRenderer = new TileRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer(10);
    private final HudRenderer hudRenderer = new HudRenderer();

    // HUD dev (đã có sẵn trong dự án)
    private volatile boolean showDev = false;
    public void setDevHud(boolean v){ showDev = v; if (hud != null) hud.setVisible(v); }

    private HudOverlay hud;
    public void setHud(HudOverlay h){ 
        this.hud = h; 
        if (hud != null) hud.setVisible(showDev);
    }
    
    public void bindChunk(rt.client.world.ChunkCache cache, int tileSize) {
        tileRenderer.setChunkCache(cache);
        tileRenderer.setTileSize(tileSize);
    }
    
    private boolean showGrid = true;
    public void setShowGrid(boolean v){ showGrid = v; }

    public GameCanvas(WorldModel model) {
        this.model = model;
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
    }

    // Giữ API cũ để không làm hỏng code cũ
    public void setPing(double v) { /* giữ chữ ký, không dùng trực tiếp ở đây */ }
    // Cho NetClient đẩy ping về
    public void setPingMs(long ms) { model.setPingMs(ms); }

    // Gọi mỗi lần khung được vẽ để cập nhật FPS (ủy quyền cho HudOverlay nếu có)
    public void onFrame() {
        if (hud != null) hud.onFrame();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final int w = getWidth(), h = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        // Tiles/pixel art cần OFF AA, stroke thuần để khỏi rung
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // ==== SAMPLE 1 LẦN (giữ nhất quán toàn khung) ====
        var snap = model.sampleForRender();                 // <-- chỉ gọi 1 lần
        rt.client.model.WorldModel.Pos youPos = null;
        String you = model.you();
        if (you != null) youPos = snap.get(you);
        if (youPos == null) youPos = model.getPredictedYou(); // fallback nếu chưa có state
        if (youPos == null) { youPos = new rt.client.model.WorldModel.Pos(); youPos.x = 0; youPos.y = 0; }

        // ==== CAMERA: dịch bằng double, KHÔNG làm tròn ====
        double camX = youPos.x * TILE;
        double camY = youPos.y * TILE;

        var oldTx = g2.getTransform();
        g2.translate(w / 2.0 - camX, h / 2.0 - camY);

        // 1) Tiles – dùng camera hiện tại, không tự recentre/không resample nữa
        tileRenderer.draw(g2, model, w, h, camX, camY);

        // 2) Grid (tuỳ chọn). Nếu thấy rung, tắt đi.
        if (showGrid) gridRenderer.drawWorldAligned(g2, w, h, TILE);

        // 3) Entities – vẽ theo snapshot đã lấy ở trên (không gọi sample lại)
        entityRenderer.draw(g2, model, TILE);

        // reset transform trước khi vẽ HUD
        g2.setTransform(oldTx);

        // 4) HUD
        hudRenderer.draw(g2, model, hud);
        if (hud != null) hud.onFrame();
        g2.dispose();
    }

}
