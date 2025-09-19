package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.ui.minimap.LocalMinimapRenderer;
import rt.client.world.ChunkCache;
import rt.client.world.WorldAtlasClient;
import rt.client.world.WorldLookup;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

/** Minimap tỉ lệ 1:10 / 1:50 với downsample địa hình thật và atlas nền. */
final class MinimapOverlay {
    private static final int TILE_PX = 32;
    private static final int MAP_SIZE = 160;
    private static final int PADDING = 16;

    private final WorldModel model;
    private WorldLookup lookup;
    private ChunkCache chunkCache;
    private WorldAtlasClient atlasClient;
    private BiConsumer<Double, Double> teleportHandler;

    private final LocalMinimapRenderer localRenderer = new LocalMinimapRenderer();

    private BufferedImage compositeBuffer;
    private BufferedImage localImage;
    private BufferedImage atlasImage;

    private double lastCenterX = Double.NaN;
    private double lastCenterY = Double.NaN;
    private long lastRedrawMs = 0L;
    private long lastAtlasMs = 0L;

    private ScaleMode scaleMode = ScaleMode.SCALE_10;
    private boolean rotateWithPlayer = false;
    private double headingRad = -Math.PI / 2.0;
    private boolean headingValid = false;
    private double lastHeadingX = Double.NaN;
    private double lastHeadingY = Double.NaN;

    MinimapOverlay(WorldModel model) {
        this.model = model;
    }

    void setLookup(WorldLookup lookup) {
        this.lookup = lookup;
        localRenderer.setLookup(lookup);
        invalidate();
    }

    void setChunkCache(ChunkCache cache) {
        this.chunkCache = cache;
        localRenderer.setChunkCache(cache);
    }

    void setAtlasClient(WorldAtlasClient atlasClient) {
        this.atlasClient = atlasClient;
    }

    void setTeleportHandler(BiConsumer<Double, Double> handler) {
        this.teleportHandler = handler;
    }

    void toggleScale() {
        scaleMode = scaleMode.next();
        invalidate();
    }

    void toggleOrientation() {
        rotateWithPlayer = !rotateWithPlayer;
    }

    private void invalidate() {
        lastCenterX = Double.NaN;
        lastCenterY = Double.NaN;
        localImage = null;
        atlasImage = null;
        lastAtlasMs = 0L;
    }

    Rectangle area(int canvasWidth, int canvasHeight) {
        int x = canvasWidth - MAP_SIZE - PADDING;
        int y = PADDING;
        return new Rectangle(x, y, MAP_SIZE, MAP_SIZE);
    }

    void draw(Graphics2D g2, int canvasWidth, int canvasHeight) {
        Rectangle rect = area(canvasWidth, canvasHeight);

        int bgX = rect.x - 6;
        int bgY = rect.y - 6;
        int bgW = rect.width + 12;
        int bgH = rect.height + 12;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(bgX, bgY, bgW, bgH, 14, 14);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.drawRoundRect(bgX, bgY, bgW, bgH, 14, 14);

        var pos = model.youPos();
        if (pos == null) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Chưa xác định vị trí", rect.x + 12, rect.y + rect.height / 2);
            return;
        }

        boolean needRedraw = Double.isNaN(lastCenterX) || Double.isNaN(lastCenterY);
        if (!needRedraw) {
            double dx = pos.x - lastCenterX;
            double dy = pos.y - lastCenterY;
            if (Math.hypot(dx, dy) > 2.0) needRedraw = true;
        }
        long now = System.currentTimeMillis();
        if (!needRedraw && now - lastRedrawMs > 750L) needRedraw = true;

        if (needRedraw) {
            int scaleWorldPx = scaleMode.worldPx;
            int radiusTiles = Math.max(1, (int) Math.ceil((MAP_SIZE * scaleWorldPx) / (2.0 * TILE_PX)));
            localImage = localRenderer.renderMini(pos.x, pos.y, radiusTiles, scaleWorldPx);

            if (atlasClient != null && atlasClient.ready() && now - lastAtlasMs > 1000L) {
                atlasImage = atlasClient.composeRegion(pos.x, pos.y, scaleWorldPx, MAP_SIZE);
                lastAtlasMs = now;
            }

            composeImages();
            lastCenterX = pos.x;
            lastCenterY = pos.y;
            lastRedrawMs = now;
        }

        updateHeading(pos.x, pos.y);

        BufferedImage img = compositeBuffer;
        if (img == null && localImage != null) img = localImage;
        if (img == null) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Đang tải minimap...", rect.x + 12, rect.y + rect.height / 2);
            return;
        }

        AffineTransform old = g2.getTransform();
        if (rotateWithPlayer && headingValid) {
            double cx = rect.getCenterX();
            double cy = rect.getCenterY();
            double rotation = -headingRad - Math.PI / 2.0;
            g2.translate(cx, cy);
            g2.rotate(rotation);
            g2.translate(-cx, -cy);
        }

        g2.drawImage(img, rect.x, rect.y, rect.width, rect.height, null);
        g2.setTransform(old);

        int cx = rect.x + rect.width / 2;
        int cy = rect.y + rect.height / 2;
        g2.setColor(Color.GREEN);
        g2.fillOval(cx - 3, cy - 3, 6, 6);
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawOval(cx - 4, cy - 4, 8, 8);
    }

    private void composeImages() {
        if (localImage == null && atlasImage == null) {
            if (compositeBuffer != null) { compositeBuffer.flush(); compositeBuffer = null; }
            return;
        }
        if (compositeBuffer == null
                || compositeBuffer.getWidth() != MAP_SIZE
                || compositeBuffer.getHeight() != MAP_SIZE) {
            if (compositeBuffer != null) compositeBuffer.flush();
            compositeBuffer = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D cg = compositeBuffer.createGraphics();
        try {
            cg.setComposite(AlphaComposite.Src);
            cg.setColor(new Color(0, 0, 0, 0));
            cg.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
            if (atlasImage != null) {
                cg.drawImage(atlasImage, 0, 0, MAP_SIZE, MAP_SIZE, null);
            }
            if (localImage != null) {
                cg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                cg.drawImage(localImage, 0, 0, MAP_SIZE, MAP_SIZE, null);
            }
        } finally {
            cg.dispose();
        }
    }

    private void updateHeading(double x, double y) {
        if (Double.isNaN(lastHeadingX) || Double.isNaN(lastHeadingY)) {
            lastHeadingX = x;
            lastHeadingY = y;
            headingValid = false;
            return;
        }
        double dx = x - lastHeadingX;
        double dy = y - lastHeadingY;
        double len = Math.hypot(dx, dy);
        if (len > 0.15) {
            headingRad = Math.atan2(dy, dx);
            headingValid = true;
            lastHeadingX = x;
            lastHeadingY = y;
        }
    }

    boolean handleClick(MouseEvent e, int canvasWidth, int canvasHeight) {
        if (teleportHandler == null) return false;
        Rectangle rect = area(canvasWidth, canvasHeight);
        if (!rect.contains(e.getPoint())) return false;

        var pos = model.youPos();
        if (pos == null) return false;

        int scaleWorldPx = scaleMode.worldPx;
        double dx = e.getX() - (rect.x + rect.width / 2.0);
        double dy = e.getY() - (rect.y + rect.height / 2.0);
        if (rotateWithPlayer && headingValid) {
            double rotation = -headingRad - Math.PI / 2.0;
            double cos = Math.cos(rotation);
            double sin = Math.sin(rotation);
            double rx = dx * cos + dy * sin;
            double ry = -dx * sin + dy * cos;
            dx = rx;
            dy = ry;
        }
        double offsetPxX = dx * scaleWorldPx;
        double offsetPxY = dy * scaleWorldPx;
        double targetX = pos.x + offsetPxX / TILE_PX;
        double targetY = pos.y + offsetPxY / TILE_PX;

        teleportHandler.accept(targetX, targetY);
        return true;
    }

    private enum ScaleMode {
        SCALE_10(10), SCALE_50(50);

        final int worldPx;

        ScaleMode(int worldPx) {
            this.worldPx = worldPx;
        }

        ScaleMode next() {
            return switch (this) {
                case SCALE_10 -> SCALE_50;
                case SCALE_50 -> SCALE_10;
            };
        }
    }
}

