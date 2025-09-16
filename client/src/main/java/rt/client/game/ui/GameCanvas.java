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
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_SPEED);


    // ==== CAMERA OFFSET (center player) ====
    int offX = 0, offY = 0;
    var you = model.you();
    if (you != null) {
    var snapshot = model.sampleForRender();
    var p = snapshot.get(you);
    if (p != null) {
    int px = (int)Math.round(p.x * TILE);
    int py = (int)Math.round(p.y * TILE);
    offX = w/2 - px;
    offY = h/2 - py;
    }
    }
    AffineTransform oldTx = g2.getTransform();
    g2.translate(offX, offY);


    // 1) Tiles (đã chuyển sang dùng atlas ở mục 2.2)
    tileRenderer.draw(g2, model, getWidth(), getHeight());


    // 2) Grid (cache) – để dưới transform để lưới đi theo camera
    gridRenderer.draw(g2, w, h, TILE, getGraphicsConfiguration());


    // 3) Entities
    entityRenderer.draw(g2, model, TILE);


    // reset transform trước khi vẽ HUD
    g2.setTransform(oldTx);


    // 4) HUD
    hudRenderer.draw(g2, model, hud);
    if (hud != null) hud.onFrame();
    g2.dispose();
    }
}
