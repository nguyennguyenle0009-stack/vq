package rt.client.game.ui.render;

import rt.client.model.WorldModel;
import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class MiniMapRenderer {
    private WorldGenConfig cfg;
    private MapRenderer renderer;

    // cấu hình mini-map
    private int mmW = 220, mmH = 140;       // kích thước pixel
    private double tilesPerPixel = 32.0;    // 1px ~ 32 tiles

    public void setConfig(WorldGenConfig cfg){
        this.cfg = cfg;
        this.renderer = (cfg != null) ? new MapRenderer(cfg) : null;
    }

    public void draw(Graphics2D g, WorldModel model, int canvasW){
        if (renderer == null) return; // chưa có seed/cfg thì thôi

        long px = Math.round(model.youX());
        long py = Math.round(model.youY());

        long originX = px - (long)(mmW * tilesPerPixel / 2.0);
        long originY = py - (long)(mmH * tilesPerPixel / 2.0);

        BufferedImage img = renderer.render(originX, originY, tilesPerPixel, mmW, mmH);

        // góc phải trên, padding 12
        int x = canvasW - mmW - 12;
        int y = 12;

        // nền + khung
        g.setColor(new Color(0,0,0,160)); g.fillRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);
        g.setColor(new Color(255,215,0,200)); g.drawRoundRect(x-6,y-6, mmW+12, mmH+12, 10,10);

        g.drawImage(img, x, y, null);

        // marker người chơi
        int mx = (int)Math.round((px - originX) / tilesPerPixel);
        int my = (int)Math.round((py - originY) / tilesPerPixel);
        g.setColor(Color.RED);
        g.fillOval(x + mx - 3, y + my - 3, 6, 6);
    }
}
