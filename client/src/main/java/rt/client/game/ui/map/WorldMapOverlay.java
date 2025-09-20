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
    private static final double BIG_TILES_PER_PIXEL = 2.0;  // tỉ lệ bản đồ lớn
    private static final int PAN_STEP_TILES = 128;

    private final WorldModel model;
    private WorldGenConfig cfg;
    private MapRenderer renderer;

    private long originX, originY;
    private double tilesPerPixel = BIG_TILES_PER_PIXEL;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"worldmap-render"); t.setDaemon(true); return t;});
    private volatile boolean busy = false;

    private final JLabel coordLabel = new JLabel("X,Y:-");
    private final JLabel scaleLabel = new JLabel("Tỉ lệ 1:" + (int)BIG_TILES_PER_PIXEL);
    private final MapPanel mapPanel = new MapPanel();

    private final JPopupMenu popup = new JPopupMenu();
    private final JMenuItem mi6 = new JMenuItem("6) Xem tọa độ");
    private final JMenuItem mi7 = new JMenuItem("7) Dịch chuyển (bật ở bước sau)");

    public WorldMapOverlay(WorldModel model){
        this.model = model;
        setOpaque(false);
        setLayout(new BorderLayout());

        // ===== LEFT: điều hướng — đặc, không trong suốt =====
        JPanel left = new JPanel();
        left.setOpaque(true);
        left.setBackground(new Color(20,35,28)); // nền đặc tối
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(240, 10));

        left.add(Box.createVerticalStrut(12));
        scaleLabel.setForeground(Color.WHITE);
        coordLabel.setForeground(Color.WHITE);
        scaleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        coordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(center(scaleLabel));
        left.add(Box.createVerticalStrut(12));
        left.add(center(coordLabel));
        left.add(Box.createVerticalStrut(16));

        JPanel arrows = new JPanel(new GridBagLayout());
        arrows.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        JButton up = new JButton("2"), leftBtn = new JButton("3"), rightBtn = new JButton("4"), down = new JButton("5");
        up.addActionListener(e -> panTiles(0, -PAN_STEP_TILES));
        down.addActionListener(e -> panTiles(0,  PAN_STEP_TILES));
        leftBtn.addActionListener(e -> panTiles(-PAN_STEP_TILES, 0));
        rightBtn.addActionListener(e -> panTiles( PAN_STEP_TILES, 0));
        c.gridx=1; c.gridy=0; arrows.add(up,c);
        c.gridx=0; c.gridy=1; arrows.add(leftBtn,c);
        c.gridx=2; c.gridy=1; arrows.add(rightBtn,c);
        c.gridx=1; c.gridy=2; arrows.add(down,c);
        left.add(center(arrows));
        left.add(Box.createVerticalStrut(12));

        JButton reload = new JButton("Reload");
        reload.setAlignmentX(Component.CENTER_ALIGNMENT);
        reload.addActionListener(e -> mapPanel.forceReload());
        left.add(center(reload));
        left.add(Box.createVerticalGlue());

        add(left, BorderLayout.WEST);

        // ===== MAP AREA — nền tối =====
        mapPanel.setOpaque(true);
        mapPanel.setBackground(new Color(10,18,14));
        add(mapPanel, BorderLayout.CENTER);

        // ===== Popup chỉ gắn vào mapPanel (không nổi ngoài) =====
     // build popup
        popup.add(mi6); popup.add(mi7);
        // BẮT BUỘC: dùng heavyweight để nổi trên overlay
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        popup.setLightWeightPopupEnabled(false);

        mi6.addActionListener(e -> {
            Point p = mapPanel.getMousePosition();
            if (p != null) {
                long gx = originX + (long)Math.floor(p.x * tilesPerPixel);
                long gy = originY + (long)Math.floor(p.y * tilesPerPixel);
                coordLabel.setText("X,Y: " + gx + "," + gy);
            }
        });
        mi7.addActionListener(e -> JOptionPane.showMessageDialog(this, "Teleport sẽ bật ở bước 2"));
    }

    private static JComponent center(JComponent c){ var p=new JPanel(new FlowLayout(FlowLayout.CENTER)); p.setOpaque(false); p.add(c); return p; }

    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255,215,0,200));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,16,16);
        g2.dispose();
    }

    public void setWorldGenConfig(WorldGenConfig cfg){
        this.cfg = cfg;
        this.renderer = (cfg != null) ? new MapRenderer(cfg) : null;
        mapPanel.img = null;
        scaleLabel.setText("Tỉ lệ 1:" + (int)Math.round(tilesPerPixel));
    }

    public void openAtPlayer(){
        long px = Math.round(model.youX()), py = Math.round(model.youY());
        int w = mapPanel.getWidth(), h = mapPanel.getHeight();
        if (w<=0 || h<=0) { SwingUtilities.invokeLater(this::openAtPlayer); return; }
        originX = px - (long)(w * tilesPerPixel / 2.0);
        originY = py - (long)(h * tilesPerPixel / 2.0);
        mapPanel.refreshAsync();
        mapPanel.requestFocusInWindow();
    }
    public void activate(){ mapPanel.requestFocusInWindow(); }
    public void deactivate(){
        MenuSelectionManager.defaultManager().clearSelectedPath(); // đóng popup nếu đang mở
        mapPanel.setComponentPopupMenu(null);                      // tách popup
        mapPanel.setComponentPopupMenu(popup);                     // gắn lại (safe)
    }
    public void panTiles(long dx, long dy){ originX += dx; originY += dy; mapPanel.refreshAsync(); }

    // ===== map panel =====
    private final class MapPanel extends JPanel {
    	private volatile BufferedImage img;

        MapPanel(){
            setOpaque(true);
            setBackground(new Color(10,18,14));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e){ maybePopup(e); }
                @Override public void mouseReleased(MouseEvent e){ maybePopup(e); }
                private void maybePopup(MouseEvent e){
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                        // show popup NGAY TẠI toạ độ click trong panel
                        popup.show(MapPanel.this, e.getX(), e.getY());
                        e.consume(); // không “lọt” ra ngoài
                    }
                }
            });
        }

        void forceReload(){ img = null; refreshAsync(); }

        void refreshAsync(){
            if (renderer == null || busy) return;
            int w = getWidth(), h = getHeight(); if (w<=0 || h<=0) return;
            busy = true;
            long ox = originX, oy = originY; double tpp = tilesPerPixel;
            exec.submit(() -> {
                BufferedImage newImg = renderer.render(ox, oy, tpp, w, h);
                SwingUtilities.invokeLater(() -> { img = newImg; busy=false; repaint(); });
            });
        }

        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if (img == null && !busy) refreshAsync();
            if (img != null) g.drawImage(img, 0, 0, null);

            // viền đỏ map-area + marker người chơi
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.RED); g2.setStroke(new BasicStroke(2f));
            g2.drawRect(1,1,getWidth()-3,getHeight()-3);

            long px = Math.round(model.youX()), py = Math.round(model.youY());
            int mx = (int)Math.round((px - originX) / tilesPerPixel);
            int my = (int)Math.round((py - originY) / tilesPerPixel);
            g2.setColor(Color.RED); g2.fillOval(mx-4, my-4, 8, 8);
        }
    }
}
