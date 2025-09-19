package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.world.ChunkCache;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Map;

/** Renderer minimap hiển thị chunk đã tải + vị trí người chơi. */
public final class MinimapRenderer {
    private static final int HUD_WIDTH = 220;
    private static final int HUD_HEIGHT = 220;
    private static final int HUD_MARGIN = 16;
    private static final double MIN_HALF_TILES = 96.0;
    private static final double PANEL_MARGIN = 48.0;
    private static final double PANEL_DEFAULT_HALF = 160.0;

    private ChunkCache chunkCache;
    private int chunkTileSizePx = 32;

    private Rectangle lastBounds = new Rectangle();
    private double lastScale = 1.0;
    private double lastCenterX = 0.0;
    private double lastCenterY = 0.0;
    private boolean hasLastState = false;
    private boolean panelOpen = false;
    private boolean viewInitialized = false;
    private double viewCenterX = 0.0;
    private double viewCenterY = 0.0;
    private double halfTiles = PANEL_DEFAULT_HALF;
    private double worldMinX = Double.NEGATIVE_INFINITY;
    private double worldMaxX = Double.POSITIVE_INFINITY;
    private double worldMinY = Double.NEGATIVE_INFINITY;
    private double worldMaxY = Double.POSITIVE_INFINITY;

    public void setChunkCache(ChunkCache cache) {
        this.chunkCache = cache;
    }

    public void setChunkTileSizePx(int px) {
        if (px <= 0) px = 1;
        this.chunkTileSizePx = px;
    }

    public boolean isPanelOpen() {
        return panelOpen;
    }

    public void setPanelOpen(boolean open, WorldModel model) {
        if (this.panelOpen == open) return;
        this.panelOpen = open;
        hasLastState = false;
        if (open) {
            viewInitialized = false;
            recenterOnPlayer(model);
            halfTiles = PANEL_DEFAULT_HALF;
        } else {
            viewInitialized = false;
        }
    }

    public void recenterOnPlayer(WorldModel model) {
        if (model == null) return;
        WorldModel.Pos you = model.youPos();
        if (you == null) you = model.getPredictedYou();
        if (you == null) return;
        viewCenterX = you.x;
        viewCenterY = you.y;
        viewInitialized = true;
    }

    /** Bounds của minimap trong toạ độ màn hình (top-right). */
    public Rectangle computeHudBounds(int canvasWidth) {
        int x = Math.max(0, canvasWidth - HUD_WIDTH - HUD_MARGIN);
        int y = HUD_MARGIN;
        return new Rectangle(x, y, HUD_WIDTH, HUD_HEIGHT);
    }

    public void draw(Graphics2D g2, WorldModel model, int canvasWidth, int canvasHeight) {
        if (model == null) return;

        WorldModel.Pos you = model.youPos();
        if (you == null) {
            you = model.getPredictedYou();
        }

        if (!panelOpen && you == null) {
            return;
        }

        Collection<ChunkCache.Data> chunks = (chunkCache != null) ? chunkCache.snapshot() : java.util.List.of();
        updateWorldBounds(chunks, you);

        if (panelOpen) {
            if (!viewInitialized && you != null) {
                viewCenterX = you.x;
                viewCenterY = you.y;
                viewInitialized = true;
            }
            drawPanel(g2, model, canvasWidth, canvasHeight, chunks);
        } else {
            if (you == null) return;
            drawHud(g2, model, canvasWidth, canvasHeight, chunks, you);
        }
    }

    public boolean contains(int x, int y) {
        return hasLastState && lastBounds.contains(x, y);
    }

    public Point2D.Double screenToWorld(int x, int y) {
        if (!panelOpen || !hasLastState || lastScale <= 0) return null;
        if (!lastBounds.contains(x, y)) return null;
        double dx = (x - lastBounds.getCenterX()) / lastScale;
        double dy = (y - lastBounds.getCenterY()) / lastScale;
        double wx = lastCenterX + dx;
        double wy = lastCenterY + dy;
        wx = clamp(wx, worldMinX, worldMaxX);
        wy = clamp(wy, worldMinY, worldMaxY);
        return new Point2D.Double(wx, wy);
    }

    public void panByWorld(double dx, double dy) {
        if (!panelOpen || !Double.isFinite(dx) || !Double.isFinite(dy)) return;
        viewCenterX = clamp(viewCenterX + dx, worldMinX + halfTiles, worldMaxX - halfTiles);
        viewCenterY = clamp(viewCenterY + dy, worldMinY + halfTiles, worldMaxY - halfTiles);
        if (worldMaxX - worldMinX <= halfTiles * 2) {
            viewCenterX = (worldMinX + worldMaxX) * 0.5;
        }
        if (worldMaxY - worldMinY <= halfTiles * 2) {
            viewCenterY = (worldMinY + worldMaxY) * 0.5;
        }
        viewInitialized = true;
    }

    public void panByScreen(int dx, int dy) {
        if (!panelOpen || lastScale <= 0) return;
        double tilesX = -dx / lastScale;
        double tilesY = -dy / lastScale;
        panByWorld(tilesX, tilesY);
    }

    private void drawHud(Graphics2D g2, WorldModel model, int canvasWidth, int canvasHeight,
                         Collection<ChunkCache.Data> chunks, WorldModel.Pos you) {
        Rectangle bounds = computeHudBounds(canvasWidth);

        double halfX = MIN_HALF_TILES;
        double halfY = MIN_HALF_TILES;
        for (ChunkCache.Data data : chunks) {
            double worldX = data.cx * (double) data.size;
            double worldY = data.cy * (double) data.size;
            halfX = Math.max(halfX, Math.abs(worldX - you.x));
            halfX = Math.max(halfX, Math.abs(worldX + data.size - you.x));
            halfY = Math.max(halfY, Math.abs(worldY - you.y));
            halfY = Math.max(halfY, Math.abs(worldY + data.size - you.y));
        }

        double half = Math.max(halfX, halfY);
        double scale = computeScale(bounds, half);
        if (scale <= 0) return;

        renderMinimap(g2, model, chunks, bounds, scale, you.x, you.y, canvasWidth, canvasHeight, false);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 12, 12);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
        g2.drawString("Minimap", bounds.x + 10, bounds.y + 18);
    }

    private void drawPanel(Graphics2D g2, WorldModel model, int canvasWidth, int canvasHeight,
                           Collection<ChunkCache.Data> chunks) {
        int pad = (int) Math.round(PANEL_MARGIN);
        int textArea = 72;
        int maxWidth = Math.max(200, canvasWidth - pad * 2);
        int maxHeight = Math.max(200, canvasHeight - pad * 2 - textArea);
        int side = Math.max(200, Math.min(maxWidth, maxHeight));
        int x = (canvasWidth - side) / 2;
        int y = pad;
        Rectangle bounds = new Rectangle(x, y, side, side);

        double scale = computeScale(bounds, halfTiles);
        if (scale <= 0) return;

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, 0, canvasWidth, canvasHeight);
        renderMinimap(g2, model, chunks, bounds, scale, viewCenterX, viewCenterY, canvasWidth, canvasHeight, true);

        g2.setColor(new Color(255, 255, 255, 220));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        g2.drawString("Bản đồ", bounds.x + 16, bounds.y + 28);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
        int textY = bounds.y + bounds.height + 24;
        g2.drawString("WASD / phím mũi tên để di chuyển. Chuột kéo để pan. Space để về nhân vật.",
                Math.max(pad, bounds.x), textY);
        g2.drawString("Click chuột phải để dịch chuyển (giới hạn trong khu vực đã khám phá).",
                Math.max(pad, bounds.x), textY + 18);
    }

    private double computeScale(Rectangle bounds, double half) {
        if (half <= 0 || !Double.isFinite(half)) return 0;
        double scaleX = bounds.getWidth() / (half * 2.0);
        double scaleY = bounds.getHeight() / (half * 2.0);
        return Math.min(scaleX, scaleY);
    }

    private void renderMinimap(Graphics2D g2, WorldModel model, Collection<ChunkCache.Data> chunks,
                               Rectangle bounds, double scale, double centerX, double centerY,
                               int canvasWidth, int canvasHeight, boolean isPanel) {
        lastBounds = new Rectangle(bounds);
        lastScale = scale;
        lastCenterX = centerX;
        lastCenterY = centerY;
        hasLastState = isPanel;

        g2.setColor(new Color(0, 0, 0, isPanel ? 200 : 150));
        g2.fillRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 12, 12);

        Shape oldClip = g2.getClip();
        g2.setClip(bounds);
        g2.setColor(new Color(10, 16, 24, 230));
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        for (ChunkCache.Data data : chunks) {
            if (chunkCache != null) {
                chunkCache.bakeImage(data, chunkTileSizePx);
            }
            if (data.img == null) continue;

            double worldX = data.cx * (double) data.size;
            double worldY = data.cy * (double) data.size;

            double destX = bounds.getCenterX() + (worldX - centerX) * scale;
            double destY = bounds.getCenterY() + (worldY - centerY) * scale;
            double destW = data.size * scale;
            double destH = destW;

            int dx = (int) Math.floor(destX);
            int dy = (int) Math.floor(destY);
            int dw = Math.max(1, (int) Math.ceil(destW));
            int dh = Math.max(1, (int) Math.ceil(destH));

            if (dx + dw < bounds.x || dy + dh < bounds.y || dx > bounds.x + bounds.width || dy > bounds.y + bounds.height) {
                continue;
            }

            g2.drawImage(data.img, dx, dy, dw, dh, null);
        }

        Map<String, WorldModel.Pos> snapshot = model.sampleForRender();
        final String youId = model.you();
        for (Map.Entry<String, WorldModel.Pos> entry : snapshot.entrySet()) {
            WorldModel.Pos pos = entry.getValue();
            int px = (int) Math.round(bounds.getCenterX() + (pos.x - centerX) * scale);
            int py = (int) Math.round(bounds.getCenterY() + (pos.y - centerY) * scale);

            if (!bounds.contains(px, py)) continue;

            if (entry.getKey().equals(youId)) {
                g2.setColor(Color.GREEN);
                g2.fillOval(px - 5, py - 5, 10, 10);
            } else {
                g2.setColor(new Color(0x80FFFF));
                g2.fillOval(px - 3, py - 3, 6, 6);
            }
        }

        double halfViewX = (canvasWidth / (double) chunkTileSizePx) / 2.0;
        double halfViewY = (canvasHeight / (double) chunkTileSizePx) / 2.0;
        int viewX = (int) Math.round(bounds.getCenterX() - halfViewX * scale);
        int viewY = (int) Math.round(bounds.getCenterY() - halfViewY * scale);
        int viewW = (int) Math.round(halfViewX * 2 * scale);
        int viewH = (int) Math.round(halfViewY * 2 * scale);

        g2.setColor(new Color(255, 255, 255, 160));
        g2.drawRect(viewX, viewY, viewW, viewH);

        int cx = (int) Math.round(bounds.getCenterX());
        int cy = (int) Math.round(bounds.getCenterY());
        g2.setColor(new Color(255, 255, 255, 120));
        g2.drawLine(cx - 6, cy, cx + 6, cy);
        g2.drawLine(cx, cy - 6, cx, cy + 6);

        g2.setClip(oldClip);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 12, 12);
    }

    private void updateWorldBounds(Collection<ChunkCache.Data> chunks, WorldModel.Pos you) {
        double minX = you != null ? you.x : 0.0;
        double maxX = you != null ? you.x : 0.0;
        double minY = you != null ? you.y : 0.0;
        double maxY = you != null ? you.y : 0.0;

        for (ChunkCache.Data data : chunks) {
            double worldX = data.cx * (double) data.size;
            double worldY = data.cy * (double) data.size;
            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            maxX = Math.max(maxX, worldX + data.size);
            maxY = Math.max(maxY, worldY + data.size);
        }

        double base = panelOpen ? Math.max(halfTiles, MIN_HALF_TILES) : MIN_HALF_TILES;
        double margin = base * 2.0;
        worldMinX = minX - margin;
        worldMaxX = maxX + margin;
        worldMinY = minY - margin;
        worldMaxY = maxY + margin;

        if (worldMinX == worldMaxX) {
            worldMinX -= 1;
            worldMaxX += 1;
        }
        if (worldMinY == worldMaxY) {
            worldMinY -= 1;
            worldMaxY += 1;
        }
    }

    private double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        if (min > max) {
            double mid = (min + max) * 0.5;
            return mid;
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
