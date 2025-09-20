// rt/client/game/ui/map/WorldMapOverlay.java
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
    // ===== config =====
    private static final double BIG_TILES_PER_PIXEL = 2.0; // tỉ lệ bản đồ lớn
    private static final int PAN_STEP_TILES = 128;

    // ===== state =====
    private final WorldModel model;
    private WorldGenConfig cfg;
    private MapRenderer renderer;

    private long originX, originY;          // tile góc trái-trên của vùng bản đồ
    private double tilesPerPixel = BIG_TILES_PER_PIXEL;

    // render nền
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"worldmap-render");t.setDaemon(true);return t;});
    private volatile boolean busy = false;

    // UI
    private final JLabel coordLabel = new JLabel("X,Y: -");
    private final MapPanel mapPanel = new MapPanel();

    public WorldMapOverlay(WorldModel model){
        this.model = model;
        setOpaque(false);
        setLayout(new BorderLayout());

        // ===== left control like full-screen =====
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(220, 10));

        left.add(Box.createVerticalStrut(16));
        coordLabel.setForeground(Color.WHITE);
        coordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(center(coordLabel));
        left.add(Box.createVerticalStrut(16));

        JPanel arrows = new JPanel(new GridBagLayout());
        arrows.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);

        JButton up = new JButton("2");
        JButton leftBtn = new JButton("3");
        JButton rightBtn = new JButton("4");
        JButton down = new JButton("5");

        up.addActionListener(e -> panTiles(0, -PAN_STEP_TILES));
        down.addActionListener(e -> panTiles(0,  PAN_STEP_TILES));
        leftBtn.addActionListener(e -> panTiles(-PAN_STEP_TILES, 0));
        rightBtn.addActionListener(e -> panTiles( PAN_STEP_TILES, 0));

        c.gridx=1; c.gridy=0; arrows.add(up, c);
        c.gridx=0; c.gridy=1; arrows.add(leftBtn, c);
        c.gridx=2; c.gridy=1; arrows.add(rightBtn, c);
        c.gridx=1; c.gridy=2; arrows.add(down, c);

        left.add(center(arrows));
        left.add(Box.createVerticalGlue());

        add(left, BorderLayout.WEST);

        // ===== map area =====
        mapPanel.setOpaque(true);
        mapPanel.setBackground(new Color(0,0,0,160));
        add(mapPanel, BorderLayout.CENTER);
    }

    private static JComponent center(JComponent c){
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    // khung & viền “vàng” giống full-screen
    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0,0,0,140));
        g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
        g2.setColor(new Color(255,215,0,200));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,16,16);
        g2.dispose();
    }

    // ===== external API =====
    public void setWorldGenConfig(WorldGenConfig cfg){
        this.cfg = cfg;
        this.renderer = (cfg != null) ? new MapRenderer(cfg) : null;
        mapPanel.img = null;
    }

    /** gọi khi bật overlay để căn tâm theo người chơi */
    public void openAtPlayer(){
        long px = Math.round(model.youX());
        long py = Math.round(model.youY());
        int w = mapPanel.getWidth(), h = mapPanel.getHeight();
        if (w <= 0 || h <= 0){ // nếu opening sớm, đợi layout xong
            SwingUtilities.invokeLater(this::openAtPlayer);
            return;
        }
        originX = px - (long)(w * tilesPerPixel / 2.0);
        originY = py - (long)(h * tilesPerPixel / 2.0);
        mapPanel.refreshAsync();
    }

    public void panTiles(long dxTiles, long dyTiles){
        originX += dxTiles; originY += dyTiles;
        mapPanel.refreshAsync();
    }

    // ===== map panel =====
    private final class MapPanel extends JPanel {
        private volatile BufferedImage img;

        MapPanel(){
            // Right-click menu: 6 xem tọa độ, 7 dịch chuyển (để bước teleport sau)
            JPopupMenu menu = new JPopupMenu();
            JMenuItem mi6 = new JMenuItem("6) Xem tọa độ");
            JMenuItem mi7 = new JMenuItem("7) Dịch chuyển (bật ở bước sau)");
            menu.add(mi6); menu.add(mi7);

            mi6.addActionListener(e -> {
                Point p = getMousePosition();
                if (p != null) {
                    long gx = originX + (long)Math.floor(p.x * tilesPerPixel);
                    long gy = originY + (long)Math.floor(p.y * tilesPerPixel);
                    coordLabel.setText("X,Y: " + gx + "," + gy);
                }
            });
            mi7.addActionListener(e -> JOptionPane.showMessageDialog(this, "Teleport sẽ bật ở bước 2."));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e){ if (e.isPopupTrigger()) showMenu(e); }
                @Override public void mouseReleased(MouseEvent e){ if (e.isPopupTrigger()) showMenu(e); }
                @Override public void mouseClicked(MouseEvent e){ if (SwingUtilities.isRightMouseButton(e)) showMenu(e); }
                private void showMenu(MouseEvent e){ menu.show(MapPanel.this, e.getX(), e.getY()); }
            });
        }

        void refreshAsync(){
            if (renderer == null || busy) return;
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            busy = true;
            final long ox = originX, oy = originY; final double tpp = tilesPerPixel;
            exec.submit(() -> {
                BufferedImage newImg = renderer.render(ox, oy, tpp, w, h);
                SwingUtilities.invokeLater(() -> { img = newImg; busy = false; repaint(); });
            });
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if (img == null && !busy) refreshAsync();
            if (img != null) g.drawImage(img, 0, 0, null);

            // viền đỏ vùng bản đồ (giống mock)
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(1,1,getWidth()-3,getHeight()-3);

            // marker người chơi
            long px = Math.round(model.youX());
            long py = Math.round(model.youY());
            int mx = (int)Math.round((px - originX) / tilesPerPixel);
            int my = (int)Math.round((py - originY) / tilesPerPixel);
            g2.setColor(Color.RED); g2.fillOval(mx-4, my-4, 8, 8);
        }
    }
}
