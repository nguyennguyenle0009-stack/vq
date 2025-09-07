package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;

import rt.common.util.DesktopDir;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientApp {
    public static void main(String[] args) throws Exception  {
        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";
        
        //Log
        Path base = DesktopDir.resolve().resolve("Vương quyền").resolve("client").resolve(name);
        Files.createDirectories(base);
        System.setProperty("VQ_LOG_DIR", base.toString());
        System.setProperty("LOG_STAMP",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")));
        System.setProperty("playerName", name);

        org.slf4j.MDC.put("player", name);

        WorldModel model = new WorldModel();
        
        NetClient net = new NetClient(url, model);
        net.connect(name);

        JFrame f = new JFrame("VQ Client - " + name);
        CanvasPanel panel = new CanvasPanel(model);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(900, 700);
        f.setLocationRelativeTo(null);
        f.setContentPane(panel);
        f.setVisible(true);

        // bắt phím
        InputState input = new InputState();
        f.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.set(e, true); }
            @Override public void keyReleased(KeyEvent e) { input.set(e, false); }
        });

        // gửi input ~30Hz
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() ->
                net.sendInput(input.up, input.down, input.left, input.right), 0, 33, TimeUnit.MILLISECONDS);

        // vẽ ~60FPS
        new Timer(16, e -> panel.repaint()).start();
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

    private static class CanvasPanel extends JPanel {
        private final WorldModel model;
        CanvasPanel(WorldModel m){ this.model = m; setBackground(Color.BLACK); }

        @Override 
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int r = 10;
            String you = model.you();

            long tRender = System.currentTimeMillis() - 100; // khớp INTERP_DELAY
            var ents = model.sample(tRender);

            if (ents.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("waiting for state...", 20, 20);
                return;
            }
            ents.forEach((id, pos) -> {
                int px = (int)Math.round(pos.x);
                int py = (int)Math.round(pos.y);
                g2.setColor(id.equals(you) ? Color.GREEN : Color.CYAN);
                g2.fillOval(px - r, py - r, r*2, r*2);
                g2.setColor(Color.WHITE);
                g2.drawString(id, px + 12, py - 12);
            });
        }
    }
}