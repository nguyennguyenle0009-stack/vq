package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.world.ChunkCache;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Map;

/** Renderer minimap hiển thị chunk đã tải + vị trí người chơi. */
public final class MinimapRenderer {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 220;
    private static final int MARGIN = 16;
    private static final double MIN_HALF_TILES = 96.0;

    private ChunkCache chunkCache;
    private int chunkTileSizePx = 32;

    private Rectangle lastBounds = new Rectangle();
    private double lastScale = 1.0;
    private double lastCenterX = 0.0;
    private double lastCenterY = 0.0;
    private boolean hasLastState = false;

    public void setChunkCache(ChunkCache cache) {
        this.chunkCache = cache;
    }

    public void setChunkTileSizePx(int px) {
        if (px <= 0) px = 1;
        this.chunkTileSizePx = px;
    }

    /** Bounds của minimap trong toạ độ màn hình (top-right). */
    public Rectangle computeBounds(int canvasWidth) {
        int x = Math.max(0, canvasWidth - WIDTH - MARGIN);
        int y = MARGIN;
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void draw(Graphics2D g2, WorldModel model, int canvasWidth, int canvasHeight) {
        if (model == null) return;

        WorldModel.Pos you = model.youPos();
        if (you == null) {
            you = model.getPredictedYou();
            if (you == null) return;
        }

        Rectangle bounds = computeBounds(canvasWidth);

        Collection<ChunkCache.Data> chunks = (chunkCache != null) ? chunkCache.snapshot() : java.util.List.of();

        double minX = you.x, maxX = you.x;
        double minY = you.y, maxY = you.y;

        for (ChunkCache.Data data : chunks) {
            double worldX = data.cx * (double) data.size;
            double worldY = data.cy * (double) data.size;
            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            maxX = Math.max(maxX, worldX + data.size);
            maxY = Math.max(maxY, worldY + data.size);
        }

        double halfX = Math.max(Math.abs(you.x - minX), Math.abs(maxX - you.x));
        double halfY = Math.max(Math.abs(you.y - minY), Math.abs(maxY - you.y));
        double half = Math.max(MIN_HALF_TILES, Math.max(halfX, halfY));
        if (!Double.isFinite(half) || half <= 0) {
            half = MIN_HALF_TILES;
        }

        double scaleX = bounds.getWidth() / (half * 2.0);
        double scaleY = bounds.getHeight() / (half * 2.0);
        double scale = Math.min(scaleX, scaleY);
        if (scale <= 0) {
            hasLastState = false;
            return;
        }

        lastBounds = new Rectangle(bounds);
        lastScale = scale;
        lastCenterX = you.x;
        lastCenterY = you.y;
        hasLastState = true;

        // Nền mờ và khung
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 12, 12);

        Shape oldClip = g2.getClip();
        g2.setClip(bounds);
        g2.setColor(new Color(10, 16, 24, 210));
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Vẽ chunk dạng ảnh đã bake (thu nhỏ)
        for (ChunkCache.Data data : chunks) {
            chunkCache.bakeImage(data, chunkTileSizePx);
            if (data.img == null) continue;

            double worldX = data.cx * (double) data.size;
            double worldY = data.cy * (double) data.size;

            double destX = bounds.getCenterX() + (worldX - you.x) * scale;
            double destY = bounds.getCenterY() + (worldY - you.y) * scale;
            double destW = data.size * scale;
            double destH = destW; // square chunk

            int dx = (int) Math.floor(destX);
            int dy = (int) Math.floor(destY);
            int dw = Math.max(1, (int) Math.ceil(destW));
            int dh = Math.max(1, (int) Math.ceil(destH));

            if (dx + dw < bounds.x || dy + dh < bounds.y || dx > bounds.x + bounds.width || dy > bounds.y + bounds.height) {
                continue;
            }

            g2.drawImage(data.img, dx, dy, dw, dh, null);
        }

        // Vẽ người chơi trên minimap
        Map<String, WorldModel.Pos> snapshot = model.sampleForRender();
        final String youId = model.you();
        for (Map.Entry<String, WorldModel.Pos> entry : snapshot.entrySet()) {
            WorldModel.Pos pos = entry.getValue();
            int px = (int) Math.round(bounds.getCenterX() + (pos.x - you.x) * scale);
            int py = (int) Math.round(bounds.getCenterY() + (pos.y - you.y) * scale);

            if (!bounds.contains(px, py)) continue;

            if (entry.getKey().equals(youId)) {
                g2.setColor(Color.GREEN);
                g2.fillOval(px - 4, py - 4, 8, 8);
            } else {
                g2.setColor(new Color(0x80FFFF));
                g2.fillOval(px - 3, py - 3, 6, 6);
            }
        }

        // Vẽ hình chữ nhật thể hiện viewport hiện tại
        double halfViewX = (canvasWidth / (double) chunkTileSizePx) / 2.0;
        double halfViewY = (canvasHeight / (double) chunkTileSizePx) / 2.0;
        int viewX = (int) Math.round(bounds.getCenterX() - halfViewX * scale);
        int viewY = (int) Math.round(bounds.getCenterY() - halfViewY * scale);
        int viewW = (int) Math.round(halfViewX * 2 * scale);
        int viewH = (int) Math.round(halfViewY * 2 * scale);

        g2.setColor(new Color(255, 255, 255, 160));
        g2.drawRect(viewX, viewY, viewW, viewH);

        // Crosshair trung tâm
        int cx = (int) Math.round(bounds.getCenterX());
        int cy = (int) Math.round(bounds.getCenterY());
        g2.setColor(new Color(255, 255, 255, 120));
        g2.drawLine(cx - 6, cy, cx + 6, cy);
        g2.drawLine(cx, cy - 6, cx, cy + 6);

        g2.setClip(oldClip);
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 12, 12);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
        g2.drawString("Minimap", bounds.x + 10, bounds.y + 18);
    }

    public boolean contains(int x, int y) {
        return hasLastState && lastBounds.contains(x, y);
    }

    public Point2D.Double screenToWorld(int x, int y) {
        if (!hasLastState || lastScale <= 0) return null;
        if (!lastBounds.contains(x, y)) return null;
        double dx = (x - lastBounds.getCenterX()) / lastScale;
        double dy = (y - lastBounds.getCenterY()) / lastScale;
        return new Point2D.Double(lastCenterX + dx, lastCenterY + dy);
    }
}
