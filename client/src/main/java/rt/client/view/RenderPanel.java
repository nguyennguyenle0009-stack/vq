package rt.client.view;

import rt.client.model.WorldModel;

import javax.swing.*;
import java.awt.*;

public class RenderPanel extends JPanel {
    private final WorldModel model;
    public RenderPanel(WorldModel m){ this.model = m; setBackground(Color.BLACK); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        int r = 10;
        String you = model.you();
        model.snapshot().forEach((id, pos) -> {
            int px = (int)Math.round(pos.x);
            int py = (int)Math.round(pos.y);
            if (id.equals(you)) {
                g2.setColor(Color.GREEN);
                g2.fillOval(px - r, py - r, r*2, r*2);
            } else {
                g2.setColor(Color.CYAN);
                g2.fillOval(px - r, py - r, r*2, r*2);
            }
            g2.setColor(Color.WHITE);
            g2.drawString(id, px + 12, py - 12);
        });
    }
}
