package rt.client.game.ui.render;

import java.awt.*;
import java.util.Map;
import rt.client.model.WorldModel;

/** Vẽ entity dạng chấm + nhãn. */
public final class EntityRenderer {
    private final int radius;

    public EntityRenderer(int radius) {
        this.radius = radius;
    }

    public void draw(Graphics2D g2, WorldModel model, int tile) {
        final int r = radius;
        final String you = model.you();
        for (Map.Entry<String, WorldModel.Pos> e : model.sampleForRender().entrySet()) {
            String id = e.getKey();
            WorldModel.Pos pos = e.getValue();
            int px = (int) Math.round(pos.x * tile);
            int py = (int) Math.round(pos.y * tile);

            g2.setColor(id.equals(you) ? Color.GREEN : Color.CYAN);
            g2.fillOval(px - r, py - r, r * 2, r * 2);

            g2.setColor(Color.WHITE);
            g2.drawString(id, px + 12, py - 12);
        }
    }
}
