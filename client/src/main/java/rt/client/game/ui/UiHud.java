package rt.client.game.ui;

import java.awt.*;

public final class UiHud {
    private final FpsCounter fps;
    private final PingMonitor ping;

    public UiHud(FpsCounter fps, PingMonitor ping) {
        this.fps = fps; this.ping = ping;
    }

    /** Vẽ HUD góc trái trên: FPS & Ping. */
    public void render(Graphics2D g2) {
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
        g2.setColor(new Color(0,0,0,150));
        g2.fillRoundRect(8, 8, 150, 40, 10, 10);

        g2.setColor(Color.WHITE);
        String sFps  = "FPS: " + fps.fpsInt();
        int p = ping.pingMs();
        String sPing = "Ping: " + (p >= 0 ? (p + " ms") : "…");
        g2.drawString(sFps, 16, 26);
        g2.drawString(sPing,16, 42);
    }
}
