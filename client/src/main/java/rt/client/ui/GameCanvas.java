package rt.client.ui;

import rt.client.model.WorldModel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Canvas vẽ mượt: cache lưới, ít cấp phát, HUD FPS/Ping. */
public class GameCanvas extends JPanel {
    public static final int TILE = 32;

    private final WorldModel model;

    // Grid cache
    private BufferedImage gridImg;
    private int gridW = -1, gridH = -1;

    // HUD
    private final Font hudFont = new Font("Consolas", Font.PLAIN, 13);
    private volatile double fpsEma = 60.0;
    private long lastPaintNs = 0L;
    private volatile long pingMs = -1;
    
    private volatile boolean showDev = false;
    public void setDevHud(boolean v){ showDev = v; }

    public GameCanvas(WorldModel model) {
        this.model = model;
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
    }

    // Cho NetClient đẩy ping về
    public void setPingMs(long ms) { this.pingMs = ms; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final int w = getWidth(), h = getHeight();
        
        var mm = model.map();
        if (mm != null) {
            g.setColor(new Color(200, 200, 200, 90)); // tường nhạt
            int T = mm.tile;
            for (int y = 0; y < mm.h; y++) {
                for (int x = 0; x < mm.w; x++) {
                    if (mm.solid[y][x]) g.fillRect(x * T, y * T, T, T);
                }
            }
        }
        
        // Rebuild grid khi đổi size
        if (gridImg == null || w != gridW || h != gridH) {
            rebuildGrid(w, h);
        }

        Graphics2D g2 = (Graphics2D) g;
        // Tắt AA vì vẽ hình học đơn giản -> nhanh hơn
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Vẽ grid đã cache
        if (gridImg != null) g2.drawImage(gridImg, 0, 0, null);

        // Vẽ entity (không tạo object tạm)
        final int r = 10; // bán kính chấm
        final String you = model.you();
        for (Map.Entry<String, WorldModel.Pos> e : model.sampleForRender().entrySet()) {
            String id = e.getKey();
            WorldModel.Pos pos = e.getValue();
            int px = (int) Math.round(pos.x * TILE);
            int py = (int) Math.round(pos.y * TILE);

            g2.setColor(id.equals(you) ? Color.GREEN : Color.CYAN);
            g2.fillOval(px - r, py - r, r * 2, r * 2);

            g2.setColor(Color.WHITE);
            g2.drawString(id, px + 12, py - 12);
        }

        // HUD: FPS/Ping
        long now = System.nanoTime();
        if (lastPaintNs != 0) {
            double dt = (now - lastPaintNs) / 1_000_000_000.0;
            double inst = dt > 0 ? (1.0 / dt) : 60.0;
            fpsEma = fpsEma * 0.9 + inst * 0.1;
        }
        lastPaintNs = now;

        g2.setFont(hudFont);
        g2.setColor(Color.WHITE);
        g2.drawString("FPS: " + Math.round(fpsEma), 8, 18);
        if (pingMs >= 0) g2.drawString("Ping: " + pingMs + " ms", 8, 34);

        var snap = model.sampleForRender();
        int entsRender = snap.size();
        int entsServer = model.serverEnts();
        g2.drawString("tick=" + model.lastTick() + " ents=" + snap.size(), 8, h - 8);
        
        if (showDev) {
            int line = 0;
            g2.setColor(new Color(0,0,0,170));
            g2.fillRoundRect(w-240, 8, 232, 104, 12, 12);
            g2.setColor(Color.GREEN);
            g2.drawString("DEV HUD", w-230, 24);
            g2.setColor(Color.WHITE);
            g2.drawString("tick: " + model.lastTick(),                w-230, 44);
            g2.drawString("ents(server): " + entsServer,              w-230, 60);
            g2.drawString("ents(render): " + entsRender,              w-230, 76);
            g2.drawString("pending inputs: " + model.pendingSize(),   w-230, 92);
            g2.drawString("dropped inputs: " + model.devDropped(),    w-230, 108);
            g2.drawString("streamer skips: " + model.devSkips() +
                          (model.devWritable() ? " (writable)" : " (backpressure)"), w-230, 124);
        }
    }

    private void rebuildGrid(int w, int h) {
        gridW = w; gridH = h;
        GraphicsConfiguration cfg = getGraphicsConfiguration();
        gridImg = (cfg != null)
                ? cfg.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gg = gridImg.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Màu lưới mờ
        gg.setColor(new Color(255, 255, 255, 20));
        for (int x = 0; x < w; x += TILE) gg.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += TILE) gg.drawLine(0, y, w, y);

        // Đường đậm mỗi 5 ô cho dễ canh
        gg.setColor(new Color(255, 255, 255, 40));
        int step5 = TILE * 5;
        for (int x = 0; x < w; x += step5) gg.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += step5) gg.drawLine(0, y, w, y);

        gg.dispose();
    }
}
