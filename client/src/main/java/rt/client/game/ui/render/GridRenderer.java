package rt.client.game.ui.render;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Vẽ lưới có cache theo kích thước panel. */
public final class GridRenderer {
    private BufferedImage gridImg;
    private int gridW = -1, gridH = -1;

    public void draw(Graphics2D g2, int w, int h, int tile, GraphicsConfiguration cfg) {
        if (gridImg == null || w != gridW || h != gridH) {
            rebuildGrid(w, h, tile, cfg);
        }
        if (gridImg != null) g2.drawImage(gridImg, 0, 0, null);
    }

    private void rebuildGrid(int w, int h, int tile, GraphicsConfiguration cfg) {
        gridW = w; gridH = h;
        gridImg = (cfg != null)
                ? cfg.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gg = gridImg.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Màu lưới mờ
        gg.setColor(new Color(255, 255, 255, 20));
        for (int x = 0; x < w; x += tile) gg.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += tile) gg.drawLine(0, y, w, y);

        // Đường đậm mỗi 5 ô cho dễ canh
        gg.setColor(new Color(255, 255, 255, 40));
        int step5 = tile * 5;
        for (int x = 0; x < w; x += step5) gg.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += step5) gg.drawLine(0, y, w, y);

        gg.dispose();
    }
}
