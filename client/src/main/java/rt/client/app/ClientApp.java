package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.common.world.WorldGenConfig;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.map.WorldMapOverlay;
import rt.client.game.ui.RenderLoop;
import rt.client.input.InputState;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientApp {
    public static void main(String[] args) throws IOException {
        final java.util.concurrent.atomic.AtomicBoolean mapOpen = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String ADMIN_TOKEN = "dev-secret-123";

        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";

        // Thư mục log
        Path base = rt.common.util.DesktopDir.resolve().resolve("Vương quyền").resolve("client").resolve(name);
        try { Files.createDirectories(base); } catch (Exception ignored) {}
        System.setProperty("VQ_LOG_DIR", base.toString());
        System.setProperty("playerName", name);
        if (System.getProperty("LOG_STAMP") == null) {
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            System.setProperty("LOG_STAMP", stamp);
        }

        // Model/Net/UI cơ bản
        WorldModel model = new WorldModel();
        NetClient net = new NetClient(url, model);
        GameCanvas panel = new GameCanvas(model);

        // Input state (khai báo TRƯỚC khi add listeners)
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
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F4"), "toggleHud");
        f.getRootPane().getActionMap().put("toggleHud", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { hud.setVisible(!hud.isVisible()); }
        });
        panel.setHud(hud);

        // ===== World Map overlay trong khung =====
        WorldMapOverlay wmOverlay = new WorldMapOverlay(model);
        wmOverlay.setVisible(false);
        layers.add(wmOverlay, JLayeredPane.DRAG_LAYER);

        // Đặt bounds overlay ~80% khung, không return giá trị (sửa lỗi bạn gặp)
        Runnable layoutOverlay = () -> {
            int w = f.getWidth(), h = f.getHeight();
            int ow = (int)(w * 0.80), oh = (int)(h * 0.80);
            int ox = (w - ow)/2, oy = (h - oh)/2;
            wmOverlay.setBounds(ox, oy, ow, oh);
        };
        layoutOverlay.run();
        f.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { layoutOverlay.run(); }
        });

        // Hotkey M: bật/tắt overlay và chặn input khi mở
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
                }
            }
        });

        // Pan overlay bằng phím mũi tên khi mapOpen
        f.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (!mapOpen.get()) return;
                int step = 128; // tiles
                switch (e.getKeyCode()) {
                    case java.awt.event.KeyEvent.VK_UP ->    wmOverlay.panTiles(0, -step);
                    case java.awt.event.KeyEvent.VK_DOWN ->  wmOverlay.panTiles(0,  step);
                    case java.awt.event.KeyEvent.VK_LEFT ->  wmOverlay.panTiles(-step, 0);
                    case java.awt.event.KeyEvent.VK_RIGHT -> wmOverlay.panTiles( step, 0);
                }
            }
        });

        // Ping HUD (client-side RTT)
        net.setOnClientPong(ns -> {
            long rttMs = (System.nanoTime() - ns) / 1_000_000L;
            panel.setPingMs(rttMs);
            hud.setPing(rttMs);
            panel.setPing(rttMs);
        });

        // Kết nối & seed → cấu hình renderer cho mini-map + overlay
        net.connect(name);
        net.setOnSeedChanged(s -> {
            var cfg = new WorldGenConfig(s, 0.50, 0.35);
            panel.setWorldGenConfig(cfg);     // mini-map
            wmOverlay.setWorldGenConfig(cfg); // map lớn
        });

        // Movement input: BỎ QUA khi mapOpen
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e)  { if (!mapOpen.get()) input.set(e, true); }
            @Override public void keyReleased(KeyEvent e) { if (!mapOpen.get()) input.set(e, false); }
        });

        // Admin hotkeys (giữ nguyên)
        f.addKeyListener(new AdminHotkeys(net, model, panel, hud, ADMIN_TOKEN));

        // Scheduler: CHỈ MỘT lần gửi input, có guard mapOpen
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> {
            if (mapOpen.get()) net.sendInput(false,false,false,false);
            else               net.sendInput(input.up, input.down, input.left, input.right);
        }, 0, 33, TimeUnit.MILLISECONDS);

        // client-ping 1s
        ses.scheduleAtFixedRate(() -> net.sendClientPing(System.nanoTime()),
                1000, 1000, TimeUnit.MILLISECONDS);

        // Render loop 60 FPS
        RenderLoop render = new RenderLoop(f, panel);
        render.start();

        // Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { render.stop(); } catch (Exception ignored) {}
            ses.shutdownNow();
        }));
    }
}
