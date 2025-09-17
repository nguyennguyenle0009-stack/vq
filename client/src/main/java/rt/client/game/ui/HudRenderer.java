package rt.client.game.ui;

import java.awt.*;

import rt.client.game.ui.hud.HudOverlay;
import rt.client.model.WorldModel;

/** Vẽ HUD text đơn giản ở góc trái (FPS/Ping). */
public final class HudRenderer {
    private final Font hudFont = new Font("Consolas", Font.PLAIN, 13);
    private volatile double fpsEma = 60.0;
    private long lastPaintNs = 0L;

    public void draw(Graphics2D g2, WorldModel model, HudOverlay hud) {
        // Tính FPS mượt nếu không có HudOverlay
        long now = System.nanoTime();
        if (lastPaintNs != 0) {
            double dt = (now - lastPaintNs) / 1_000_000_000.0;
            double inst = dt > 0 ? (1.0 / dt) : 60.0;
            fpsEma = fpsEma * 0.9 + inst * 0.1;
        }
        lastPaintNs = now;

        double fps = (hud != null) ? hud.getFps() : fpsEma;
        double pingMs = model.pingText();

        g2.setFont(hudFont);
        g2.setColor(Color.WHITE);
        g2.drawString(String.format("FPS: %.0f   Ping: %.0f ms", fps, pingMs), 10, 10);
        g2.drawString(String.format("X: %.2f   Y: %.2f", model.youX(), model.youY()), 10, 25);
    }
}
