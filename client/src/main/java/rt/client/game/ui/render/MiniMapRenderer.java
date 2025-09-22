// rt/client/game/ui/render/MiniMapRenderer.java
package rt.client.game.ui.render;

import rt.client.model.WorldModel;
import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;

public final class MiniMapRenderer {
    private WorldGenConfig cfg;
    private MapRenderer renderer;

    // cấu hình: vùng phủ nhỏ để nhẹ (1px ~ 6 tiles)
    private int mmW = 220, mmH = 140;
    private double tilesPerPixel = 1.0;

    // cache ảnh + render nền
    private volatile BufferedImage last;
    private volatile boolean busy = false;
    private long lastCenterX = Long.MIN_VALUE, lastCenterY = Long.MIN_VALUE;
    private long lastAtNs = 0L;
    private static final long COOL_DOWN_NS = 200_000_000L; // 200ms

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

        long px = Math.round(model.youX());
        long py = Math.round(model.youY());
        long originX = px - (long)(mmW * tilesPerPixel / 2.0);
        long originY = py - (long)(mmH * tilesPerPixel / 2.0);

        // chỉ phát lệnh render khi: (1) chưa có ảnh, (2) di chuyển vượt 1px, (3) qua cooldown
        long now = System.nanoTime();
        boolean movedEnough = Math.abs(px - lastCenterX) >= (long)tilesPerPixel
                           || Math.abs(py - lastCenterY) >= (long)tilesPerPixel;

        if (!busy && (last == null || (movedEnough && now - lastAtNs > COOL_DOWN_NS))) {
            busy = true; lastCenterX = px; lastCenterY = py;
            final long ox = originX, oy = originY;
            exec.submit(() -> {
                BufferedImage img = renderer.render(ox, oy, tilesPerPixel, mmW, mmH);
                last = img; lastAtNs = System.nanoTime(); busy = false;
                SwingUtilities.invokeLater(repaintOwner::repaint);
            });
        }

        // vẽ khung & ảnh cache (nếu có)
        int x = canvasW - mmW - 12, y = 12;
        g.setColor(new Color(0,0,0,160)); g.fillRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);
        g.setColor(new Color(255,215,0,200)); g.drawRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);
        if (last != null) g.drawImage(last, x, y, null);

        // marker người chơi
        int mx = (int)Math.round((px - originX) / tilesPerPixel);
        int my = (int)Math.round((py - originY) / tilesPerPixel);
        g.setColor(Color.RED);
        g.fillOval(x + mx - 3, y + my - 3, 6, 6);
        
        String scale = "1:" + (int)Math.round(tilesPerPixel);
        g.setFont(g.getFont().deriveFont(10f));
        var fm = g.getFontMetrics();
        int labelW = fm.stringWidth(scale);
        g.setColor(new java.awt.Color(0,0,0,160));
        g.fillRect(x + mmW - labelW - 8, y + 4, labelW + 6, fm.getHeight());
        g.setColor(java.awt.Color.WHITE);
        g.drawString(scale, x + mmW - labelW - 5, y + 4 + fm.getAscent());

    }
}
