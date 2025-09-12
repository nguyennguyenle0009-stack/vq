package rt.client.game.ui.tile;

import java.awt.*;
import rt.client.model.WorldModel;

/** Vẽ tile map (ô solid). */
public final class TileRenderer {
    private static final Color WALL = new Color(200, 200, 200, 90);

    public void draw(Graphics2D g2, WorldModel model) {
        var mm = model.map();
        if (mm == null) return;
        g2.setColor(WALL);
        int T = mm.tile;
        for (int y = 0; y < mm.h; y++) {
            for (int x = 0; x < mm.w; x++) {
                if (mm.solid[y][x]) g2.fillRect(x * T, y * T, T, T);
            }
        }
    }
}
