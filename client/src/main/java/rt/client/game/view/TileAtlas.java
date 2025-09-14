package rt.client.game.view;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class TileAtlas {
    private final BufferedImage sheet;
    private final int tile, cols;

    public TileAtlas(String path, int tile, int cols) throws Exception {
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("tileset not found: " + path);
            sheet = ImageIO.read(in);
        }
        this.tile=tile; this.cols=cols;
    }

    public void draw(java.awt.Graphics2D g2, int id, int dx, int dy){
        if (id <= 0) return;
        int sx = (id % cols) * tile;
        int sy = (id / cols) * tile;
        if (sx + tile > sheet.getWidth() || sy + tile > sheet.getHeight()) return;
        g2.drawImage(sheet, dx,dy, dx+tile,dy+tile, sx,sy, sx+tile,sy+tile, null);
    }
}
