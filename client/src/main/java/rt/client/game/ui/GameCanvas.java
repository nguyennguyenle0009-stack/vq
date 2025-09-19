package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.render.EntityRenderer;
import rt.client.game.ui.render.GridRenderer;
import rt.client.game.ui.tile.TileRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
    private final MinimapRenderer minimapRenderer = new MinimapRenderer();

    private BiConsumer<Double, Double> minimapTeleport;
    private boolean minimapDrag = false;
    private int minimapLastDragX = 0;
    private int minimapLastDragY = 0;
    private boolean minimapToggleHeld = false;
    private boolean minimapPanUp = false;
    private boolean minimapPanDown = false;
    private boolean minimapPanLeft = false;
    private boolean minimapPanRight = false;
    private long minimapLastPanNs = System.nanoTime();

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
        minimapRenderer.setChunkCache(cache);
        minimapRenderer.setChunkTileSizePx(tileSize);
    }

    public void setOnMinimapTeleport(BiConsumer<Double, Double> cb) {
        this.minimapTeleport = cb;
    }

    public boolean isMinimapPanelOpen() {
        return minimapRenderer.isPanelOpen();
    }

    private boolean showGrid = false;
    public void setShowGrid(boolean v){ showGrid = v; }

    public GameCanvas(WorldModel model) {
        this.model = model;
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) return;
                if (!minimapRenderer.isPanelOpen()) return;
                Point2D.Double world = minimapRenderer.screenToWorld(e.getX(), e.getY());
                if (world == null) return;
                if (minimapTeleport != null) {
                    minimapTeleport.accept(world.x, world.y);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!minimapRenderer.isPanelOpen()) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (!minimapRenderer.contains(e.getX(), e.getY())) return;
                minimapDrag = true;
                minimapLastDragX = e.getX();
                minimapLastDragY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    minimapDrag = false;
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!minimapRenderer.isPanelOpen() || !minimapDrag) return;
                int dx = e.getX() - minimapLastDragX;
                int dy = e.getY() - minimapLastDragY;
                if (dx != 0 || dy != 0) {
                    minimapRenderer.panByScreen(dx, dy);
                    minimapLastDragX = e.getX();
                    minimapLastDragY = e.getY();
                    repaint();
                }
            }
        });
    }

    public boolean handleKeyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_M) {
            if (!minimapToggleHeld) {
                toggleMinimapPanel();
            }
            minimapToggleHeld = true;
            return true;
        }
        if (!minimapRenderer.isPanelOpen()) return false;

        return switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> {
                setMinimapPanelOpen(false);
                yield true;
            }
            case KeyEvent.VK_W, KeyEvent.VK_UP -> { minimapPanUp = true; yield true; }
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> { minimapPanDown = true; yield true; }
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> { minimapPanLeft = true; yield true; }
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> { minimapPanRight = true; yield true; }
            case KeyEvent.VK_SPACE -> {
                minimapRenderer.recenterOnPlayer(model);
                repaint();
                yield true;
            }
            default -> false;
        };
    }

    public boolean handleKeyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_M) {
            minimapToggleHeld = false;
            return true;
        }
        if (!minimapRenderer.isPanelOpen()) return false;

        return switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> { minimapPanUp = false; yield true; }
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> { minimapPanDown = false; yield true; }
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> { minimapPanLeft = false; yield true; }
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> { minimapPanRight = false; yield true; }
            default -> false;
        };
    }

    private void toggleMinimapPanel() {
        setMinimapPanelOpen(!minimapRenderer.isPanelOpen());
    }

    private void setMinimapPanelOpen(boolean open) {
        if (open == minimapRenderer.isPanelOpen()) return;
        minimapRenderer.setPanelOpen(open, model);
        minimapPanUp = minimapPanDown = minimapPanLeft = minimapPanRight = false;
        minimapDrag = false;
        minimapLastPanNs = System.nanoTime();
        repaint();
    }

    private void tickMinimapPan() {
        if (!minimapRenderer.isPanelOpen()) {
            minimapLastPanNs = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        double dt = (now - minimapLastPanNs) / 1_000_000_000.0;
        minimapLastPanNs = now;
        if (dt <= 0) return;

        double vx = 0;
        double vy = 0;
        if (minimapPanLeft) vx -= 1;
        if (minimapPanRight) vx += 1;
        if (minimapPanUp) vy -= 1;
        if (minimapPanDown) vy += 1;

        if (vx == 0 && vy == 0) return;

        double len = Math.hypot(vx, vy);
        if (len > 0) {
            vx /= len;
            vy /= len;
        }

        final double SPEED = 120.0; // tiles per second
        minimapRenderer.panByWorld(vx * SPEED * dt, vy * SPEED * dt);
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

        AffineTransform oldTransform = g2.getTransform();

        g2.translate(w/2 - (int)Math.round(camX), h/2 - (int)Math.round(camY));

        tileRenderer.draw(g2, model);                 // vẽ chunk-images
        if (showGrid) gridRenderer.draw(g2, w, h, TILE, getGraphicsConfiguration());
        entityRenderer.draw(g2, model, TILE);

        g2.setTransform(oldTransform);
        hudRenderer.draw(g2, model, hud);
        tickMinimapPan();
        minimapRenderer.draw(g2, model, w, h);
        if (hud != null) hud.onFrame();
        g2.dispose();
    }

}
