package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.ui.GameCanvas;
import rt.client.ui.HudOverlay;

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
	     Path base = rt.common.util.DesktopDir.resolve()
	             .resolve("Vương quyền").resolve("client").resolve(name);
	     try { Files.createDirectories(base); } catch (Exception ignored) {}
	
	     // Set system properties cho Logback
	     System.setProperty("VQ_LOG_DIR", base.toString());
	     System.setProperty("playerName", name);
	     if (System.getProperty("LOG_STAMP") == null) {
	         String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
	                 .format(new java.util.Date());
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
        hud.setBounds(0,0,f.getWidth(),f.getHeight());
        layers.add(hud, JLayeredPane.PALETTE_LAYER);
        f.addComponentListener(new java.awt.event.ComponentAdapter(){
            @Override public void componentResized(java.awt.event.ComponentEvent e){ hud.setBounds(0,0,f.getWidth(),f.getHeight()); }
        });
        f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F4"), "toggleHud");
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

        // Bắt phím
        InputState input = new InputState();
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.set(e, true); }
            @Override public void keyReleased(KeyEvent e) { input.set(e, false); }
        });
        final boolean[] devHud = {false};
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_F1 -> net.sendAdmin(ADMIN_TOKEN, "listSessions");
                    case KeyEvent.VK_F2 -> {
                        String you = model.you();
                        if (you != null) net.sendAdmin(ADMIN_TOKEN, "teleport " + you + " 5 5");
                    }
                    case KeyEvent.VK_F3 -> net.sendAdmin(ADMIN_TOKEN, "reloadMap");
                    case KeyEvent.VK_F4 -> { devHud[0] = !devHud[0]; panel.setDevHud(devHud[0]); }
                }
                input.set(e, true);
            }
            @Override public void keyReleased(KeyEvent e) { input.set(e, false); }
        });

        // Gửi input ~30Hz + cping 1s
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() ->
                net.sendInput(input.up, input.down, input.left, input.right), 0, 33, TimeUnit.MILLISECONDS);
        ses.scheduleAtFixedRate(() ->
                net.sendClientPing(System.nanoTime()), 1000, 1000, TimeUnit.MILLISECONDS);

        // Render loop 60 FPS (repaint được gọi từ thread riêng; EDT sẽ vẽ)
        Thread render = new Thread(() -> {
            final long frameNs = 16_666_667L;
            long next = System.nanoTime();
            while (f.isDisplayable()) {
                long now = System.nanoTime();
                if (now < next) {
                    long sleepMs = Math.max(0, (next - now) / 1_000_000);
                    if (sleepMs > 0) {
                        try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
                    }
                    continue;
                }
                panel.repaint(); // safe: chỉ schedule vẽ trên EDT
                next += frameNs;
            }
        }, "render-loop");
        render.setDaemon(true);
        render.start();

        // Shutdown gọn
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ses.shutdownNow();
        }));
    }

    private static class InputState {
        volatile boolean up, down, left, right;
        
        void set(KeyEvent e, boolean v) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W, KeyEvent.VK_UP -> up = v;
                case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = v;
                case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = v;
                case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = v;
            }
        }
    }
}
