package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.world.ChunkCache;
import rt.client.world.map.MapRenderer;
import rt.common.world.WorldGenConfig;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.map.WorldMapOverlay;
import rt.client.gfx.TerrainTextures;
import rt.client.gfx.skin.ChunkSkins;
import rt.client.game.ui.RenderLoop;
import rt.client.input.InputState;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientApp {

    public static void main(String[] args) throws IOException {
    	
        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";
    	
        // Model/Net/UI
        WorldModel model = new WorldModel();
        NetClient net = new NetClient(url, model);
        GameCanvas panel = new GameCanvas(model); // GameCanvas đã setFocusable(true)
        
        ChunkSkins.init();                    // đăng ký sprite (hoặc để trống dùng màu)
        MapRenderer.setCache(net.chunkCache());
    	
    	final java.util.concurrent.atomic.AtomicBoolean mapOpen = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String ADMIN_TOKEN = "dev-secret-123";

        // Thư mục log
        Path base = rt.common.util.DesktopDir.resolve().resolve("Vương quyền").resolve("client").resolve(name);
        try { Files.createDirectories(base); } catch (Exception ignored) {}
        System.setProperty("VQ_LOG_DIR", base.toString());
        System.setProperty("playerName", name);
        if (System.getProperty("LOG_STAMP") == null) {
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            System.setProperty("LOG_STAMP", stamp);
        }



        // Input state
        InputState input = new InputState();

        // Bind chunk renderer
        panel.bindChunk(net.chunkCache(), net.tileSize());
        net.setOnTileSizeChanged(ts -> panel.bindChunk(net.chunkCache(), ts));

        // Frame
        JFrame f = new JFrame("VQ Client - " + name);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(1100, 700);
        f.setLocationRelativeTo(null);
        f.setContentPane(panel);
        f.setVisible(true);

        // Đảm bảo Canvas nhận focus ngay
        SwingUtilities.invokeLater(() -> {
            panel.setFocusable(true);
            panel.requestFocusInWindow();
            panel.requestFocus();
        });

        // HUD dev (F4)
        HudOverlay hud = new HudOverlay(model);
        JLayeredPane layers = f.getLayeredPane();
        hud.setBounds(0, 0, f.getWidth(), f.getHeight());
        layers.add(hud, JLayeredPane.PALETTE_LAYER);
        f.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                hud.setBounds(0, 0, f.getWidth(), f.getHeight());
            }
        });
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F4"), "toggleHud");
        f.getRootPane().getActionMap().put("toggleHud", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { hud.setVisible(!hud.isVisible()); }
        });
        panel.setHud(hud);

        // ===== World Map overlay trong khung =====
        WorldMapOverlay wmOverlay = new WorldMapOverlay(model, net);
        wmOverlay.setVisible(false);
        wmOverlay.setTeleportHandler((gx, gy) -> {
            String you = model.you();
            if (you != null) {
                net.sendAdmin(ADMIN_TOKEN, "teleport " + you + " " + gx + " " + gy);
            }
        });
        layers.add(wmOverlay, JLayeredPane.MODAL_LAYER);

        // Đặt bounds overlay theo ô (trên/trái/phải: 2 ô; dưới: 3 ô)
        Runnable layoutOverlay = () -> {
            int ts = net.tileSize(); if (ts <= 0) ts = 32;
            int w = f.getWidth(), h = f.getHeight();
            int mTop = 2*ts, mLeft = 2*ts, mRight = 2*ts, mBottom = 3*ts;
            int ow = Math.max(200, w - mLeft - mRight);
            int oh = Math.max(150, h - mTop - mBottom);
            wmOverlay.setBounds(mLeft, mTop, ow, oh);
        };
        layoutOverlay.run();
        f.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e){ layoutOverlay.run(); }
        });

        // Toggle WorldMap (M)
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('M'), "toggleWorldMap");
        f.getRootPane().getActionMap().put("toggleWorldMap", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!wmOverlay.isVisible()) {
                    wmOverlay.setVisible(true);
                    wmOverlay.openAtPlayer();
                    mapOpen.set(true);
                } else {
                    wmOverlay.setVisible(false);
                    mapOpen.set(false);

                    // ---- FIX kẹt phím & focus ----
                    input.up = input.down = input.left = input.right = false;
                    net.sendInput(false,false,false,false);
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                    SwingUtilities.invokeLater(() -> {
                        panel.setFocusable(true);
                        panel.requestFocusInWindow();
                        panel.requestFocus();
                    });
                }
            }
        });

        // Pan overlay bằng mũi tên khi map mở
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "mapPanUp");
        f.getRootPane().getActionMap().put("mapPanUp", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) wmOverlay.panTiles(0, -128);
            }
        });
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "mapPanDown");
        f.getRootPane().getActionMap().put("mapPanDown", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) wmOverlay.panTiles(0, 128);
            }
        });
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "mapPanLeft");
        f.getRootPane().getActionMap().put("mapPanLeft", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) wmOverlay.panTiles(-128, 0);
            }
        });
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "mapPanRight");
        f.getRootPane().getActionMap().put("mapPanRight", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) wmOverlay.panTiles(128, 0);
            }
        });

        // ===== Movement bằng Key Bindings (WASD + mũi tên), có guard mapOpen =====
        bindMove(f.getRootPane(), mapOpen, input,
                KeyStroke.getKeyStroke("pressed W"), KeyStroke.getKeyStroke("released W"),
                KeyStroke.getKeyStroke("pressed S"), KeyStroke.getKeyStroke("released S"),
                KeyStroke.getKeyStroke("pressed A"), KeyStroke.getKeyStroke("released A"),
                KeyStroke.getKeyStroke("pressed D"), KeyStroke.getKeyStroke("released D"));

        bindMove(f.getRootPane(), mapOpen, input,
                KeyStroke.getKeyStroke("pressed UP"),    KeyStroke.getKeyStroke("released UP"),
                KeyStroke.getKeyStroke("pressed DOWN"),  KeyStroke.getKeyStroke("released DOWN"),
                KeyStroke.getKeyStroke("pressed LEFT"),  KeyStroke.getKeyStroke("released LEFT"),
                KeyStroke.getKeyStroke("pressed RIGHT"), KeyStroke.getKeyStroke("released RIGHT"));

        // Nếu cửa sổ mất focus → clear input (tránh kẹt phím ngầm)
        f.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus(java.awt.event.WindowEvent e) {
                input.up = input.down = input.left = input.right = false;
                net.sendInput(false,false,false,false);
            }
        });

        // Ping HUD
        net.setOnClientPong(ns -> {
            long rttMs = (System.nanoTime() - ns) / 1_000_000L;
            panel.setPingMs(rttMs);
            hud.setPing(rttMs);
            panel.setPing(rttMs);
        });

        // Kết nối & seed
        net.connect(name);
        net.setOnSeedChanged(s -> {
            // DÙNG CHUNG VỚI SERVER (MainServer)
        	var cfg = new rt.common.world.WorldGenConfig(
    		    s, 0.55, 0.35   // desert = 1 - 0.55 - 0.35 = 0.10
    		);
    		panel.setWorldGenConfig(cfg);
    		wmOverlay.setWorldGenConfig(cfg);
    		layoutOverlay.run();
        });
        // Admin hotkeys
        f.addKeyListener(new AdminHotkeys(net, model, panel, hud, ADMIN_TOKEN));

        // Gửi input định kỳ
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> {
            if (mapOpen.get()) net.sendInput(false,false,false,false);
            else               net.sendInput(input.up, input.down, input.left, input.right);
        }, 0, 33, TimeUnit.MILLISECONDS);

        // client-ping 1s
        ses.scheduleAtFixedRate(() -> net.sendClientPing(System.nanoTime()), 1000, 1000, TimeUnit.MILLISECONDS);

        // Render loop 60 FPS
        RenderLoop render = new RenderLoop(f, panel);
        render.start();

        // Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { render.stop(); } catch (Exception ignored) {}
            ses.shutdownNow();
        }));
    }

    /** Bind WASD/Arrows vào InputState qua RootPane để không phụ thuộc focus con. */
    private static void bindMove(JRootPane root,
                                 java.util.concurrent.atomic.AtomicBoolean mapOpen,
                                 InputState input,
                                 KeyStroke upPress, KeyStroke upRelease,
                                 KeyStroke downPress, KeyStroke downRelease,
                                 KeyStroke leftPress, KeyStroke leftRelease,
                                 KeyStroke rightPress, KeyStroke rightRelease) {

        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(upPress, "mv_up_p");     am.put("mv_up_p", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.up   = true; }});
        im.put(upRelease, "mv_up_r");   am.put("mv_up_r", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.up   = false; }});

        im.put(downPress, "mv_dn_p");   am.put("mv_dn_p", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.down = true; }});
        im.put(downRelease, "mv_dn_r"); am.put("mv_dn_r", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.down = false; }});

        im.put(leftPress, "mv_lt_p");   am.put("mv_lt_p", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.left = true; }});
        im.put(leftRelease, "mv_lt_r"); am.put("mv_lt_r", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.left = false; }});

        im.put(rightPress, "mv_rt_p");  am.put("mv_rt_p", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.right= true; }});
        im.put(rightRelease, "mv_rt_r");am.put("mv_rt_r", new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(!mapOpen.get()) input.right= false; }});
    }
}
