package rt.client.view;

import rt.client.model.WorldModel;

import javax.swing.*;
import java.awt.*;

/** JPanel vẽ nền, "you" (xanh), "others" (xám). */
public class RenderPanel extends JPanel {
    private final WorldModel model;
    public RenderPanel(WorldModel m){ this.model = m; }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(24,27,34));
        g2.fillRect(0,0,getWidth(),getHeight());

        // others
        g2.setColor(new Color(180,180,180));
        model.forEachOther(p -> draw(g2, p.x, p.y, 14));

        // you
        var me = model.getMe();
        g2.setColor(new Color(80,180,255));
        draw(g2, me.x, me.y, 16);

        g2.setColor(new Color(220,220,220));
        g2.drawString("Players: " + model.count(), 10, 18);
    }

    private void draw(Graphics2D g2, double x, double y, int r) {
        int ix = (int)Math.round(x), iy = (int)Math.round(y);
        g2.fillOval(ix - r, iy - r, r*2, r*2);
    }
}
