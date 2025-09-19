package rt.client.game.ui.map;

import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;
import rt.client.model.WorldModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Dialog toàn màn hình hiển thị world map. Bước 1: pan + xem tọa độ (6). */
public final class WorldMapScreen extends JDialog {
    // --- cấu hình đơn giản cho bước 1 ---
    private static final double DEFAULT_TILES_PER_PIXEL = 32.0; // zoom 1: 1px ~ 32 tiles
    private static final int PAN_STEP_TILES = 128;              // bước pan với nút 2/3/4/5

    private final JLabel coordLabel = new JLabel("X,Y: -");
    private final MapPanel mapPanel = new MapPanel();

    private final MapRenderer renderer;
    private final WorldModel worldModel;

    private long originX, originY;
    private double tilesPerPixel = DEFAULT_TILES_PER_PIXEL;

    private long lastClickGX, lastClickGY;

    public static void showModal(Window owner, WorldModel wm, WorldGenConfig cfg){
        WorldMapScreen dlg = new WorldMapScreen(owner, wm, cfg);
        dlg.setVisible(true);
    }

    private WorldMapScreen(Window owner, WorldModel wm, WorldGenConfig cfg){
        super(owner, "World Map", ModalityType.MODELESS);
        this.worldModel = wm;
        this.renderer = new MapRenderer(cfg);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setLocationRelativeTo(null);

        // origin tại giữa người chơi
        long px = Math.round(wm.youX());
        long py = Math.round(wm.youY());
        originX = px - (long)(getWidth()  * tilesPerPixel / 2.0);
        originY = py - (long)(getHeight() * tilesPerPixel / 2.0);

        JPanel left = buildLeftControls();
        mapPanel.setOpaque(true);
        mapPanel.setBackground(Color.BLACK);

        setLayout(new BorderLayout());
        add(left, BorderLayout.WEST);
        add(mapPanel, BorderLayout.CENTER);

        // phím ESC để đóng
        mapPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        mapPanel.getActionMap().put("close", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ dispose(); } });

        // lần đầu vẽ
        //mapPanel.refresh();
        setLayout(new BorderLayout());
        add(left, BorderLayout.WEST);
        add(mapPanel, BorderLayout.CENTER);
        SwingUtilities.invokeLater(mapPanel::refresh);
    }

    private JPanel buildLeftControls(){
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setPreferredSize(new Dimension(240, getHeight()));
        wrap.setBackground(new Color(0,0,0,130));

        JPanel pad = new JPanel();
        pad.setOpaque(false);
        pad.setLayout(new BoxLayout(pad, BoxLayout.Y_AXIS));
        pad.add(Box.createVerticalStrut(24));
        coordLabel.setForeground(Color.WHITE);
        coordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        pad.add(center(coordLabel));
        pad.add(Box.createVerticalStrut(24));

        // cụm nút pan (2/3/4/5)
        JPanel arrows = new JPanel(new GridBagLayout());
        arrows.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);

        JButton up = new JButton("2");
        JButton left = new JButton("3");
        JButton right = new JButton("4");
        JButton down = new JButton("5");

        up.addActionListener(e -> { originY -= PAN_STEP_TILES; mapPanel.refresh(); });
        down.addActionListener(e -> { originY += PAN_STEP_TILES; mapPanel.refresh(); });
        left.addActionListener(e -> { originX -= PAN_STEP_TILES; mapPanel.refresh(); });
        right.addActionListener(e -> { originX += PAN_STEP_TILES; mapPanel.refresh(); });

        c.gridx=1; c.gridy=0; arrows.add(up, c);
        c.gridx=0; c.gridy=1; arrows.add(left, c);
        c.gridx=2; c.gridy=1; arrows.add(right, c);
        c.gridx=1; c.gridy=2; arrows.add(down, c);

        pad.add(center(arrows));
        pad.add(Box.createVerticalGlue());

        wrap.add(pad, BorderLayout.CENTER);
        return wrap;
    }

    private static JComponent center(JComponent c){
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private final class MapPanel extends JPanel {
        private BufferedImage img;

        MapPanel(){
            // right-click menu
            JPopupMenu menu = new JPopupMenu();
            JMenuItem viewCoord = new JMenuItem("6) Xem tọa độ");
            JMenuItem teleport  = new JMenuItem("7) Dịch chuyển (Bước 2)");
            menu.add(viewCoord);
            menu.add(teleport);

            viewCoord.addActionListener(e -> coordLabel.setText("X,Y: " + lastClickGX + "," + lastClickGY));
            teleport.addActionListener(e -> JOptionPane.showMessageDialog(this, "Sẽ bật ở Bước 2 (Teleport)."));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e){ if (e.isPopupTrigger()) showMenu(e); }
                @Override public void mouseReleased(MouseEvent e){ if (e.isPopupTrigger()) showMenu(e); }
                @Override public void mouseClicked(MouseEvent e){
                    if (SwingUtilities.isRightMouseButton(e)) showMenu(e);
                }
                private void showMenu(MouseEvent e){
                    long gx = originX + (long)Math.floor(e.getX() * tilesPerPixel);
                    long gy = originY + (long)Math.floor(e.getY() * tilesPerPixel);
                    lastClickGX = gx; lastClickGY = gy;
                    menu.show(MapPanel.this, e.getX(), e.getY());
                }
            });
        }

        void refresh() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;              // <<< quan trọng: tránh width/height=0
            img = renderer.render(originX, originY, tilesPerPixel, w, h);
            repaint();
        }
        
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if (img == null) refresh();                // lazy lần đầu sau khi có kích thước
            if (img != null) g.drawImage(img, 0, 0, null);

            // marker người chơi
            long px = Math.round(worldModel.youX());
            long py = Math.round(worldModel.youY());
            int mx = (int)Math.round((px - originX) / tilesPerPixel);
            int my = (int)Math.round((py - originY) / tilesPerPixel);
            g.setColor(Color.RED);
            g.fillOval(mx-3, my-3, 6, 6);
        }
    }
}
