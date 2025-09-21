package rt.client.game.ui.map;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.common.world.WorldGenConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

public final class WorldMapOverlay extends JComponent {
    private final WorldModel model;
    private final NetClient net;

    private rt.client.world.map.MapRenderer mapRenderer;

    // gốc hiển thị (tile) & tỉ lệ (tiles / pixel)
    private long originX = 0, originY = 0;
    private int tilesPerPixel = 1; // yêu cầu: mặc định 1
    private BufferedImage lastImg;
    private int lastRenderW = 0, lastRenderH = 0;
    private double lastRenderTpp = tilesPerPixel;

    private final JLabel scaleLabel = new JLabel("Tỉ lệ 1:1");
    private final JLabel placeLabel = new JLabel("—");
    private final javax.swing.JLabel coordLabel = new javax.swing.JLabel("X: —   Y: —");
    private final JButton reloadBtn = new JButton("Reload");

    private BiConsumer<Long,Long> teleportHandler;

    private final JComponent mapPanel;

    // lưu vị trí click để popup dùng
    private int lastClickX = -1, lastClickY = -1;

    public WorldMapOverlay(WorldModel model, NetClient net) {
        this.model = model;
        this.net   = net;

        setLayout(new BorderLayout());
        setOpaque(false);

        // ==== Header ====
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(true);
        top.setBackground(new Color(0x16,0x16,0x16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        left.setOpaque(false);
        scaleLabel.setForeground(Color.WHITE);
        placeLabel.setForeground(Color.LIGHT_GRAY);
        left.add(scaleLabel);
        left.add(new JLabel("  |  "));
        left.add(placeLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        
        right.setOpaque(false);
        coordLabel.setForeground(java.awt.Color.WHITE);   // dễ đọc trên nền tối
        right.add(coordLabel);
        reloadBtn.addActionListener(e -> { lastImg = null; refresh(); });
        right.add(reloadBtn);

        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // ==== Map panel ====
        mapPanel = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2.setColor(new Color(0x12,0x13,0x14));
                g2.fillRect(0,0,getWidth(),getHeight());

                if (lastImg != null)
                    g2.drawImage(lastImg, 0, 0, getWidth(), getHeight(), null);

                // vẽ vị trí người chơi
                var you = model.getPredictedYou();
                if (you == null && model.you()!=null) { var s=model.sampleForRender(); you=s.get(model.you()); }
                if (you != null && lastRenderW>0 && lastRenderH>0) {
                    double rx = (you.x - originX) / lastRenderTpp;
                    double ry = (you.y - originY) / lastRenderTpp;
                    double sx = getWidth()/(double)lastRenderW, sy = getHeight()/(double)lastRenderH;
                    int px = (int)Math.round(rx*sx), py = (int)Math.round(ry*sy);
                    if (px>=0 && py>=0 && px<getWidth() && py<getHeight()){
                        g2.setColor(new Color(220,30,30));
                        g2.fillOval(px-3,py-3,7,7);
                        g2.setColor(Color.BLACK);
                        g2.drawOval(px-3,py-3,7,7);
                    }
                }

                // viền
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(255,210,0));
                g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,10,10);
                g2.dispose();
            }
        };
        mapPanel.setOpaque(true);
        mapPanel.setBackground(new Color(0x12,0x13,0x14));
        add(mapPanel, BorderLayout.CENTER);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { refresh(); }
        });

        // ==== Popup menu (chuột phải) ====
        JPopupMenu menu = new JPopupMenu();
        menu.setLightWeightPopupEnabled(false); // ép dùng heavyweight
        mapPanel.setComponentPopupMenu(menu);   // để AWT tự hiện popup
        JMenuItem miInfo = new JMenuItem("6) Xem tọa độ");
        JMenuItem miTp   = new JMenuItem("7) Dịch chuyển");
        menu.add(miInfo); menu.add(miTp);

        // cách 1: gán trực tiếp để AWT tự hiện popup (đảm bảo luôn thấy)
        mapPanel.setComponentPopupMenu(menu);

        // cách 2: tự xử lý + lưu tọa độ click (bắt cả pressed & released + right button)
        mapPanel.addMouseListener(new java.awt.event.MouseAdapter() {
        	  private void showIfNeeded(java.awt.event.MouseEvent e){
        	    lastClickX = e.getX(); lastClickY = e.getY();
        	    if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
        	      menu.show(mapPanel, lastClickX, lastClickY);
        	    }
        	  }
        	  @Override public void mousePressed (java.awt.event.MouseEvent e){ showIfNeeded(e); }
        	  @Override public void mouseReleased(java.awt.event.MouseEvent e){ showIfNeeded(e); }
        	});

        miInfo.addActionListener(ev -> {
            long[] gxy = toGlobalTile(lastClickX, lastClickY);
            net.sendGeoReq(gxy[0], gxy[1]);
        });
        miTp.addActionListener(ev -> {
            if (teleportHandler != null) {
                long[] gxy = toGlobalTile(lastClickX, lastClickY);
                teleportHandler.accept(gxy[0], gxy[1]);
            }
        });
        
        miInfo.addActionListener(ev -> {
            long[] gxy = toGlobalTile(lastClickX, lastClickY);
            // HIỂN THỊ TỌA ĐỘ NGAY
            javax.swing.SwingUtilities.invokeLater(() ->
                coordLabel.setText("X: " + gxy[0] + "   Y: " + gxy[1])
            );
            // Gửi yêu cầu địa danh như cũ
            net.sendGeoReq(gxy[0], gxy[1]);
        });

        // nhận GeoS2C → cập nhật địa danh
        net.setOnGeoInfo(gi -> SwingUtilities.invokeLater(() -> {
            String text = (gi.seaId() > 0)
                ? "Biển – " + (gi.seaName()!=null? gi.seaName() : "—") + " – —"
                : "Biển – " + (gi.continentName()!=null? gi.continentName() : "—")
                           + " – " + (gi.terrainName()!=null? gi.terrainName() : "—");
            placeLabel.setText(text);
        }));

        // ==== Pan + Zoom (±1, min=1, max=8) ====
        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = getActionMap();
        am.put("panL", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ panTiles(-128,0);} });
        am.put("panR", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ panTiles( 128,0);} });
        am.put("panU", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ panTiles(0,-128);} });
        am.put("panD", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ panTiles(0, 128);} });

        am.put("zoomIn",  new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ setScaleTilesPerPixel(tilesPerPixel - 1); }});
        am.put("zoomOut", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ setScaleTilesPerPixel(tilesPerPixel + 1); }});
        im.put(KeyStroke.getKeyStroke("LEFT"),  "panL");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "panR");
        im.put(KeyStroke.getKeyStroke("UP"),    "panU");
        im.put(KeyStroke.getKeyStroke("DOWN"),  "panD");
        im.put(KeyStroke.getKeyStroke('='),     "zoomIn");  // +
        im.put(KeyStroke.getKeyStroke('-'),     "zoomOut"); // -

        addHierarchyListener(e -> { if (isShowing()) requestFocusInWindow(); });
    }

    // ===== API =====
    public void setWorldGenConfig(WorldGenConfig cfg){
        this.mapRenderer = new rt.client.world.map.MapRenderer(cfg);
        refresh();
    }
    public void setTeleportHandler(BiConsumer<Long,Long> handler){ this.teleportHandler = handler; }

    public void openAtPlayer(){
        double x=0, y=0;
        var you = model.getPredictedYou();
        if (you == null && model.you()!=null){ var s=model.sampleForRender(); var p=s.get(model.you()); if (p!=null){ x=p.x; y=p.y; } }
        else if (you!=null){ x=you.x; y=you.y; }

        int w = Math.max(1, mapPanel.getWidth()), h = Math.max(1, mapPanel.getHeight());
        long visW = (long)Math.floor(w * tilesPerPixel), visH = (long)Math.floor(h * tilesPerPixel);
        originX = (long)Math.floor(x) - visW/2; originY = (long)Math.floor(y) - visH/2;
        refresh();
    }
    public void panTiles(long dx,long dy){ originX += dx; originY += dy; refresh(); }

    public void setScaleTilesPerPixel(int tpp){
        tilesPerPixel = Math.max(1, Math.min(8, tpp));
        scaleLabel.setText("Tỉ lệ 1:" + tilesPerPixel);
        refresh();
    }
    private void setScaleTilesPerPixel(double tpp){ setScaleTilesPerPixel((int)Math.round(tpp)); }

    public void activate(){ setVisible(true); requestFocusInWindow(); }
    public void deactivate(){ setVisible(false);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
    }

    // ===== Render =====
    private void refresh(){
        if (mapRenderer == null) return;
        int w = Math.max(1, mapPanel.getWidth()), h = Math.max(1, mapPanel.getHeight());

        // Adaptive: giữ ~0.8 MP
        final int MAX_PIXELS = 800_000;
        int wR=w, hR=h; double tpp=tilesPerPixel;
        while ((long)wR*hR > MAX_PIXELS) { wR=Math.max(1,wR/2); hR=Math.max(1,hR/2); tpp *= 2.0; }

        lastImg = mapRenderer.render(originX, originY, tpp, wR, hR);
        lastRenderW = wR; lastRenderH = hR; lastRenderTpp = tpp;
        repaint();
    }

    private long[] toGlobalTile(int cx, int cy){
        double rx = cx / (getWidth() /(double)Math.max(1,lastRenderW));
        double ry = cy / (getHeight()/(double)Math.max(1,lastRenderH));
        long gx = originX + (long)Math.floor(rx * lastRenderTpp);
        long gy = originY + (long)Math.floor(ry * lastRenderTpp);
        return new long[]{gx,gy};
    }
}
