package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;
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
        final String ADMIN_TOKEN = "dev-secret-123"; // đổi nếu đổi trong server-config.json

        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";

        // Tạo thư mục: Desktop/Vương quyền/client/<name>
        Path base = rt.common.util.DesktopDir.resolve().resolve("Vương quyền").resolve("client").resolve(name);
        try { Files.createDirectories(base); } catch (Exception ignored) {}

        // Set system properties cho Logback
        System.setProperty("VQ_LOG_DIR", base.toString());
        System.setProperty("playerName", name);
        if (System.getProperty("LOG_STAMP") == null) {
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            System.setProperty("LOG_STAMP", stamp);
        }

        WorldModel model = new WorldModel();
        NetClient net = new NetClient(url, model);

        // UI
        JFrame f = new JFrame("VQ Client - " + name);
        GameCanvas panel = new GameCanvas(model);
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
        // Toggle HUD bằng InputMap (F4)
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F4"), "toggleHud");
        f.getRootPane().getActionMap().put("toggleHud", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { hud.setVisible(!hud.isVisible()); }
        });
        panel.setHud(hud);

        // Ping HUD (client-side RTT)
        net.setOnClientPong(ns -> {
            long rttMs = (System.nanoTime() - ns) / 1_000_000L;
            panel.setPingMs(rttMs);
            hud.setPing(rttMs);
            panel.setPing(rttMs);
        });

        // Kết nối
        net.connect(name);

        // Input
        InputState input = new InputState();
        // Lắng nghe phím chuyển động
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.set(e, true); }
            @Override public void keyReleased(KeyEvent e) { input.set(e, false); }
        });
        // Lắng nghe phím admin/hotkeys
        f.addKeyListener(new AdminHotkeys(net, model, panel, hud, ADMIN_TOKEN));

        // Gửi input ~30Hz + client-ping 1s
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> net.sendInput(input.up, input.down, input.left, input.right),
                0, 33, TimeUnit.MILLISECONDS);
        ses.scheduleAtFixedRate(() -> net.sendClientPing(System.nanoTime()),
                1000, 1000, TimeUnit.MILLISECONDS);

        // Render loop 60 FPS
        RenderLoop render = new RenderLoop(f, panel);
        render.start();

        // Shutdown gọn
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { render.stop(); } catch (Exception ignored) {}
            ses.shutdownNow();
        }));
    }
}
