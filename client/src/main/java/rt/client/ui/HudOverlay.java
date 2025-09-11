package rt.client.ui;

import javax.swing.*;
import java.awt.*;
import rt.client.model.WorldModel;

/** HUD dev bật/tắt bằng F4. */
public final class HudOverlay extends JComponent {
	private final WorldModel model;
	private volatile double fps = 0.0, ping = 0.0;
	public HudOverlay(WorldModel m){ this.model=m; setOpaque(false); setVisible(false); }
	public void setFps(double v){ fps=v; }
	public void setPing(double v){ ping=v; }
	
	@Override 
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	    g2.setColor(new Color(0,0,0,140)); g2.fillRoundRect(8,8,300,120,12,12);
	    g2.setColor(Color.WHITE);
	    int y=28;
	    g2.drawString(String.format("FPS: %.0f   Ping: %.0f ms", fps, ping), 16,y); y+=18;
	    g2.drawString("Tick(render est): " + model.renderTickEstimate(), 16,y); y+=18;
	    g2.drawString("Ents(server/render): " + model.devEntsServer() + " / " + model.sampleForRender().size(), 16,y);y+=18;
	    g2.drawString("Pending: " + model.pendingSize() + "  Dropped: " + model.devDroppedInputs(), 16,y); y+=18;
	    g2.drawString("Streamer skips: " + model.devStreamerSkips() + "  Writable: " + model.devWritable(), 16,y);
	}
}
