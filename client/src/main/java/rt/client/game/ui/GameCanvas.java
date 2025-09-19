package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.render.EntityRenderer;
import rt.client.game.ui.render.GridRenderer;
import rt.client.game.ui.tile.TileRenderer;
import rt.client.world.WorldLookup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.function.BiConsumer;

/** Canvas vẽ mượt, tách riêng renderers: grid / tile / entity / HUD text. */
public class GameCanvas extends JPanel {
    public static final int TILE = 32;

    private final WorldModel model;

    // Renderers tách riêng
    private final GridRenderer gridRenderer = new GridRenderer();
    private final TileRenderer tileRenderer = new TileRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer(10);
    private final HudRenderer hudRenderer = new HudRenderer();
    private final MinimapOverlay minimap;

    private WorldLookup lookup;

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
        minimap.setChunkCache(cache);
    }
    
    private boolean showGrid = false;
    public void setShowGrid(boolean v){ showGrid = v; }


    public void setLookup(WorldLookup lookup) {
        this.lookup = lookup;
        if (minimap != null) {
            minimap.setLookup(lookup);
        }
    }
    public void setAtlasClient(rt.client.world.WorldAtlasClient atlas) {
        minimap.setAtlasClient(atlas);
    }
    public void setMinimapTeleportHandler(BiConsumer<Double, Double> handler) {
        minimap.setTeleportHandler(handler);
    }
    public GameCanvas(WorldModel model) {
        this.model = model;
        this.minimap = new MinimapOverlay(model);
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (minimap.handleClick(e, getWidth(), getHeight())) {
                        e.consume();
                    }
                }
            }
        });
    }

    // Giữ API cũ để không làm hỏng code cũ
    public void setPing(double v) { /* giữ chữ ký, không dùng trực tiếp ở đây */ }
    // Cho NetClient đẩy ping về
    public void setPingMs(long ms) { model.setPingMs(ms); }

    // Gọi mỗi lần khung được vẽ để cập nhật FPS (ủy quyền cho HudOverlay nếu có)
    public void onFrame() {
        if (hud != null) hud.onFrame();
    }

    private double camX = 0, camY = 0;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        int w = getWidth(), h = getHeight();

        var youId = model.you();
        rt.client.model.WorldModel.Pos you = model.getPredictedYou();
        if (you == null && youId != null) {
            var snap = model.sampleForRender(); // có thể tối ưu thành sampleOne(id)
            you = snap.get(youId);
        }
        if (you != null) {
            double targetX = you.x * TILE, targetY = you.y * TILE;
            double alpha = 0.18; // 0.1–0.25 tuỳ gu mượt
            camX += (targetX - camX) * alpha;
            camY += (targetY - camY) * alpha;
        }

        AffineTransform previous = g2.getTransform();
        g2.translate(w/2 - (int)Math.round(camX), h/2 - (int)Math.round(camY));

        tileRenderer.draw(g2, model);                 // vẽ chunk-images
        if (showGrid) gridRenderer.draw(g2, w, h, TILE, getGraphicsConfiguration());
        entityRenderer.draw(g2, model, TILE);

        g2.setTransform(previous);

        minimap.draw(g2, w, h);
        hudRenderer.draw(g2, model, hud, lookup);
        if (hud != null) hud.onFrame();
        g2.dispose();
    }

    public void toggleMinimapScale() {
        minimap.toggleScale();
    }

    public void toggleMinimapOrientation() {
        minimap.toggleOrientation();
    }

}
