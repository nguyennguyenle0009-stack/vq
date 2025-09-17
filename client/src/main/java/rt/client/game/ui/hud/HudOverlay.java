package rt.client.game.ui.hud;

import javax.swing.*;
import java.awt.*;
import rt.client.model.WorldModel;

/** HUD dev bật/tắt bằng F4. */
public final class HudOverlay extends JComponent {
	  private final WorldModel model;
	  private volatile double fps = 0.0, ping = 0.0;

	  // >>> thêm 2 biến đếm khung
	  private long lastFpsNs = System.nanoTime();
	  private int frames = 0;

	  public HudOverlay(WorldModel m){ this.model=m; setOpaque(false); setVisible(false); }

	  public void setPing(double v){ ping=v; }

	  // >>> gọi mỗi lần khung được vẽ để cập nhật FPS
	  public void onFrame() {
	    long now = System.nanoTime();
	    frames++;
	    if (now - lastFpsNs >= 500_000_000L) { // ~0.5s
	      fps = frames * 1_000_000_000.0 / (now - lastFpsNs);
	      frames = 0;
	      lastFpsNs = now;
	    }
	  }

	  @Override protected void paintComponent(Graphics g) {
	    super.paintComponent(g);
	    Graphics2D g2 = (Graphics2D) g;
	    final int w = getWidth();
	    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	    int y=28;
	    g2.setColor(new Color(0,0,0,140)); g2.fillRoundRect(w-240,y,190,120,12,12);y+=18;;
	    g2.setColor(Color.WHITE);
	    
	    g2.drawString(String.format("FPS: %.0f   Ping: %.0f ms", fps, model.pingText()), w-230,y); y+=18;
	    g2.drawString("Tick(render est): " + model.renderTickEstimate(), w-230,y); y+=18;
	    g2.drawString("Ents(server/render): " + model.devEntsServer() + " / " + model.sampleForRender().size(), w-230,y); y+=18;
	    g2.drawString("Pending: " + model.pendingSize() + "  Dropped: " + model.devDroppedInputs(), w-230,y); y+=18;
	    g2.drawString("Streamer skips: " + model.devStreamerSkips() + "  Writable: " + model.devWritable(), w-230,y);
	  }

	  public double getFps() {
		  return fps;
	  }

}
