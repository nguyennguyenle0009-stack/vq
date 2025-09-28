// rt/client/game/ui/render/MiniMapRenderer.java
package rt.client.game.ui.render;

import rt.client.model.WorldModel;
import rt.client.world.ChunkCache;
import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;

public final class MiniMapRenderer {
    private static ChunkCache STATIC_CACHE;
    public static void setCache(ChunkCache cache) { STATIC_CACHE = cache; }

    private WorldGenConfig cfg;
    private MapRenderer renderer;

    // Khung minimap (pixel) — bạn giữ nguyên kích thước UI mong muốn
    private int mmW = 220, mmH = 140;

    // === cache ảnh + render nền ===
    private volatile BufferedImage last;
    private volatile boolean busy = false;
    private long lastCenterX = Long.MIN_VALUE, lastCenterY = Long.MIN_VALUE;
    private long lastAtNs = 0L;
    private static final long COOL_DOWN_NS = 350_000_000L; // 350ms

    // Thông tin ảnh minimap đã render (để map marker chính xác)
    private volatile long   lastOx = 0, lastOy = 0;
    private volatile double lastTpp = 1.0; // tiles per pixel
    private volatile int    lastW = 1, lastH = 1; // kích thước ảnh minimap (vuông)

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minimap-render"); t.setDaemon(true); return t;
    });

    public void setConfig(WorldGenConfig cfg){
        this.cfg = cfg;
        this.renderer = (cfg != null) ? new MapRenderer(cfg) : null;
        this.last = null;
        this.lastCenterX = this.lastCenterY = Long.MIN_VALUE;
    }

    public void draw(Graphics2D g, WorldModel model, int canvasW, JComponent repaintOwner){
        if (renderer == null) return;

        // ====== Tính cấu hình 41×41 tiles ======
        final int MINI_TILES = 41;
        // Dùng cạnh ngắn để đảm bảo đúng 41 ô theo cạnh ngắn
        final int viewPx = Math.max(1, Math.min(mmW, mmH)); // ảnh render vuông viewPx×viewPx
        final double tpp = (double) MINI_TILES / (double) viewPx; // tiles per pixel

        // Tâm là người chơi (toạ độ tile, làm tròn để tâm “đứng”)
        long px = Math.round(model.youX());
        long py = Math.round(model.youY());

        // Gốc toạ độ để người chơi nằm giữa ảnh 41×41
        long originX = px - (MINI_TILES / 2);
        long originY = py - (MINI_TILES / 2);

        // ====== Lên lịch render nền khi cần ======
        long now = System.nanoTime();
        boolean movedEnough = Math.abs(px - lastCenterX) >= 1 || Math.abs(py - lastCenterY) >= 1;

        if (!busy && (last == null || (movedEnough && now - lastAtNs > COOL_DOWN_NS))) {
            busy = true; lastCenterX = px; lastCenterY = py;
            final long ox = originX, oy = originY;
            exec.submit(() -> {
                BufferedImage img = renderer.render(ox, oy, tpp, viewPx, viewPx); // MapRenderer: dùng getImageTemp + scale
                last = img; lastAtNs = System.nanoTime();
                lastOx = ox; lastOy = oy; lastTpp = tpp; lastW = viewPx; lastH = viewPx;
                busy = false;
                SwingUtilities.invokeLater(repaintOwner::repaint);
            });
        }

        // ====== Vẽ khung minimap ======
        int x = canvasW - mmW - 12, y = 12;
        g.setColor(new Color(0,0,0,160)); g.fillRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);
        g.setColor(new Color(255,215,0,200)); g.drawRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);

        // Ảnh cache: vẽ scale để điền đầy khung mmW×mmH
        if (last != null) {
            g.drawImage(last, x, y, x + mmW, y + mmH, 0, 0, lastW, lastH, null);
        }

        // ====== Vẽ marker người chơi tại tâm của ảnh đã render ======
//        if (last != null && lastW > 0 && lastH > 0) {
//            double rx = (px - lastOx) / lastTpp;
//            double ry = (py - lastOy) / lastTpp;
//            double sx = mmW / (double) lastW;
//            double sy = mmH / (double) lastH;
//            int mx = x + (int)Math.round(rx * sx);
//            int my = y + (int)Math.round(ry * sy);
//            g.setColor(Color.RED);
//            g.fillOval(mx - 3, my - 3, 6, 6);
//            g.setColor(Color.BLACK);
//            g.drawOval(mx - 3, my - 3, 6, 6);
//        }

        // Nhãn tỉ lệ (hiển thị 41×41)
        String scale = "41×41";
        g.setFont(g.getFont().deriveFont(10f));
        var fm = g.getFontMetrics();
        int labelW = fm.stringWidth(scale);
        g.setColor(new java.awt.Color(0,0,0,160));
        g.fillRect(x + mmW - labelW - 8, y + 4, labelW + 6, fm.getHeight());
        g.setColor(java.awt.Color.WHITE);
        g.drawString(scale, x + mmW - labelW - 5, y + 4 + fm.getAscent());
    }
}
