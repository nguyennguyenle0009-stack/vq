package rt.client.game.ui.map;

import rt.client.model.WorldModel;
import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;

public final class WorldMapOverlay extends JPanel {
    private final WorldModel model;
    private WorldGenConfig cfg;
    private MapRenderer renderer;

    // tỉ lệ bản đồ lớn
    private double tilesPerPixel = 2.0; // BIG_TPP

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"worldmap-render");t.setDaemon(true);return t;});
    private volatile BufferedImage img;
    private volatile boolean busy;

    private long originX, originY;      // tile góc trái-trên khung

    public WorldMapOverlay(WorldModel model){
        this.model = model;
        setOpaque(false);
        // menu right-click: xem tọa độ + (teleport sẽ làm ở bước sau)
        JPopupMenu menu = new JPopupMenu();
        JMenuItem miCoord = new JMenuItem("6) Xem tọa độ");
        menu.add(miCoord);
        miCoord.addActionListener(e -> {
            Point p = getMousePosition();
            if (p != null){
                long gx = originX + (long)Math.floor(p.x * tilesPerPixel);
                long gy = originY + (long)Math.floor(p.y * tilesPerPixel);
                JOptionPane.showMessageDialog(this, "X: "+gx+"  Y: "+gy);
            }
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){ if (e.isPopupTrigger()) menu.show(WorldMapOverlay.this, e.getX(), e.getY()); }
            @Override public void mouseReleased(MouseEvent e){ if (e.isPopupTrigger()) menu.show(WorldMapOverlay.this, e.getX(), e.getY()); }
        });
    }

    public void setWorldGenConfig(WorldGenConfig cfg){
        this.cfg = cfg;
        this.renderer = (cfg != null) ? new MapRenderer(cfg) : null;
        this.img = null;
    }

    /** gọi khi bật overlay để căn tâm theo người chơi */
    public void openAtPlayer(){
        long px = Math.round(model.youX());
        long py = Math.round(model.youY());
        originX = px - (long)(getWidth()  * tilesPerPixel / 2.0);
        originY = py - (long)(getHeight() * tilesPerPixel / 2.0);
        refreshAsync();
    }

    public void panTiles(long dxTiles, long dyTiles){
        originX += dxTiles; originY += dyTiles;
        refreshAsync();
    }

    private void refreshAsync(){
        if (renderer == null) return;
        int w = getWidth(), h = getHeight();
        if (w<=0 || h<=0 || busy) return;
        busy = true;
        final long ox = originX, oy = originY; final double tpp = tilesPerPixel;
        exec.submit(() -> {
            BufferedImage newImg = renderer.render(ox, oy, tpp, w, h);
            SwingUtilities.invokeLater(() -> { img = newImg; busy = false; repaint(); });
        });
    }

    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        // nền mờ + khung
        g2.setColor(new Color(0,0,0,160)); g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
        g2.setColor(new Color(255,215,0,200)); g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);
        if (img == null && !busy) refreshAsync();
        if (img != null) g2.drawImage(img, 0, 0, null);

        // marker người chơi
        long px = Math.round(model.youX());
        long py = Math.round(model.youY());
        int mx = (int)Math.round((px - originX) / tilesPerPixel);
        int my = (int)Math.round((py - originY) / tilesPerPixel);
        g2.setColor(Color.RED); g2.fillOval(mx-4, my-4, 8, 8);

        g2.dispose();
    }
}
