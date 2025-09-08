package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.ui.GameCanvas;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientApp {
    public static void main(String[] args) {
        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";

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

        // Ping HUD (client-side RTT)
        net.setOnClientPong(ns -> {
            long rttMs = (System.nanoTime() - ns) / 1_000_000L;
            panel.setPingMs(rttMs);
        });

        // Kết nối
        net.connect(name);

        // Bắt phím
        InputState input = new InputState();
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.set(e, true); }
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
