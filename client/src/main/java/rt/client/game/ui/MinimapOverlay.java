package rt.client.game.ui;

import rt.client.model.WorldModel;
import rt.client.world.WorldLookup;
import rt.common.world.BiomeId;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.function.BiConsumer;

/**
 * Minimap 1:100 hiển thị quanh người chơi và hỗ trợ click phải để teleport.
 */
final class MinimapOverlay {
    private static final int SCALE = 100; // 1 pixel = 100 tile
    private static final int MAP_SIZE = 168; // kích thước vùng vẽ bên trong
    private static final int PADDING = 16;

    private static final int DEFAULT_COLOR = 0xFF7F00FF; // magenta => dễ phát hiện nếu thiếu map
    private static final int[] COLOR_TABLE = new int[256];

    static {
        register(BiomeId.OCEAN, 0xFF083B83);
        register(BiomeId.LAND, 0xFF556B2F);
        register(BiomeId.PLAIN, 0xFF3DA940);
        register(BiomeId.PLAIN_WEIRD, 0xFF66CDAA);
        register(BiomeId.DESERT, 0xFFE1C16E);
        register(BiomeId.FOREST, 0xFF196C2E);
        register(BiomeId.FOREST_FOG, 0xFF0F4A2C);
        register(BiomeId.FOREST_MAGIC, 0xFF2F4F7F);
        register(BiomeId.FOREST_WEIRD, 0xFF4F2F7F);
        register(BiomeId.FOREST_DARK, 0xFF061E15);
        register(BiomeId.LAKE, 0xFF1D5DBE);
        register(BiomeId.RIVER, 0xFF4F90E3);
        register(BiomeId.MOUNTAIN_SNOW, 0xFFF5F8FF);
        register(BiomeId.MOUNTAIN_VOLCANO, 0xFFB7410E);
        register(BiomeId.MOUNTAIN_FOREST, 0xFF4A5D23);
        register(BiomeId.MOUNTAIN_ROCK, 0xFF8B8680);
        register(BiomeId.VILLAGE, 0xFFCD2F2F);
    }

    private static void register(int id, int argb) {
        COLOR_TABLE[id & 0xFF] = argb;
    }

    private final WorldModel model;
    private WorldLookup lookup;
    private BiConsumer<Double, Double> teleportHandler;

    private BufferedImage buffer;
    private double lastCenterX = Double.NaN;
    private double lastCenterY = Double.NaN;
    private long lastRedrawMs = 0L;

    MinimapOverlay(WorldModel model) {
        this.model = model;
    }

    void setLookup(WorldLookup lookup) {
        this.lookup = lookup;
        this.buffer = null; // force redraw với lookup mới
    }

    void setTeleportHandler(BiConsumer<Double, Double> handler) {
        this.teleportHandler = handler;
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

        if (lookup == null || !lookup.ready()) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Đang tải minimap...", rect.x + 12, rect.y + rect.height / 2);
            return;
        }

        var pos = model.youPos();
        if (pos == null) {
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Chưa xác định vị trí", rect.x + 12, rect.y + rect.height / 2);
            return;
        }

        ensureBuffer(rect.width, rect.height);

        boolean needRedraw = Double.isNaN(lastCenterX) || Double.isNaN(lastCenterY);
        if (!needRedraw) {
            double dx = pos.x - lastCenterX;
            double dy = pos.y - lastCenterY;
            if (Math.hypot(dx, dy) > 5.0) needRedraw = true; // di chuyển >5 tile
        }
        long now = System.currentTimeMillis();
        if (!needRedraw && now - lastRedrawMs > 750L) {
            needRedraw = true; // cập nhật định kỳ để phản ánh thay đổi chunk
        }

        if (needRedraw) {
            redrawBuffer(buffer, pos.x, pos.y);
            lastCenterX = pos.x;
            lastCenterY = pos.y;
            lastRedrawMs = now;
        }

        g2.drawImage(buffer, rect.x, rect.y, null);

        // đánh dấu người chơi ở giữa
        int cx = rect.x + rect.width / 2;
        int cy = rect.y + rect.height / 2;
        g2.setColor(Color.GREEN);
        g2.fillOval(cx - 3, cy - 3, 6, 6);
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawOval(cx - 4, cy - 4, 8, 8);
    }

    private void ensureBuffer(int width, int height) {
        if (buffer == null || buffer.getWidth() != width || buffer.getHeight() != height) {
            buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            lastCenterX = Double.NaN;
            lastCenterY = Double.NaN;
        }
    }

    private void redrawBuffer(BufferedImage img, double centerX, double centerY) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < h; y++) {
            double gy = centerY + (y - h / 2.0) * SCALE;
            long sampleY = (long) Math.floor(gy);
            for (int x = 0; x < w; x++) {
                double gx = centerX + (x - w / 2.0) * SCALE;
                long sampleX = (long) Math.floor(gx);
                int base = lookup.baseId(sampleX, sampleY);
                int argb = colorFor(base);
                pixels[y * w + x] = argb;
            }
        }
    }

    private static int colorFor(int base) {
        int idx = base & 0xFF;
        int color = COLOR_TABLE[idx];
        return color != 0 ? color : DEFAULT_COLOR;
    }

    boolean handleClick(MouseEvent e, int canvasWidth, int canvasHeight) {
        if (lookup == null || !lookup.ready() || teleportHandler == null) return false;
        Rectangle rect = area(canvasWidth, canvasHeight);
        if (!rect.contains(e.getPoint())) return false;

        var pos = model.youPos();
        if (pos == null) return false;

        double offsetX = (e.getX() - (rect.x + rect.width / 2.0)) * SCALE;
        double offsetY = (e.getY() - (rect.y + rect.height / 2.0)) * SCALE;
        double targetX = pos.x + offsetX;
        double targetY = pos.y + offsetY;

        teleportHandler.accept(targetX, targetY);
        return true;
    }
}

