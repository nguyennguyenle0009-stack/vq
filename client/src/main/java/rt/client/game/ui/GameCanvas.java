package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.render.EntityRenderer;
import rt.client.game.ui.render.GridRenderer;
import rt.client.game.ui.tile.TileRenderer;

import javax.swing.*;
import java.awt.*;

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
        Graphics2D g2 = (Graphics2D) g;

        // Tắt AA vì vẽ hình học đơn giản -> nhanh hơn
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // 1) Vẽ tile map (tường/ô solid)
        tileRenderer.draw(g2, model);

        // 2) Vẽ lưới (có cache theo kích thước)
        gridRenderer.draw(g2, w, h, TILE, getGraphicsConfiguration());

        // 3) Vẽ entity (chấm + nhãn)
        entityRenderer.draw(g2, model, TILE);

        // 4) HUD text đơn giản (FPS/Ping)
        hudRenderer.draw(g2, model, hud);

        // Cập nhật FPS cho HudOverlay (nếu có sử dụng)
        if (hud != null) hud.onFrame();
    }
}
