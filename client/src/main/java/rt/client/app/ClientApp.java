package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static rt.common.game.Units.*; // TILE_PX, toPx(...)

public class ClientApp {
    public static void main(String[] args) {
        String url = "ws://localhost:8090/ws";
        String name = args.length > 0 ? args[0] : "Player";

        WorldModel model = new WorldModel();
        NetClient net = new NetClient(url, model);
        net.connect(name);

        JFrame f = new JFrame("VQ Client - " + name);
        CanvasPanel panel = new CanvasPanel(model);
        panel.setFocusable(true);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(WORLD_W_TILES * TILE_PX + 40, WORLD_H_TILES * TILE_PX + 60);
        f.setLocationRelativeTo(null);
        f.setContentPane(panel);
        f.setVisible(true);
        panel.requestFocusInWindow();

        // Bắt phím trên panel (tránh mất focus)
        InputState input = new InputState();
        panel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { input.set(e, true); }
            @Override public void keyReleased(KeyEvent e) { input.set(e, false); }
        });
        
        // gửi input ~30Hz
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() ->
                net.sendInput(input.up, input.down, input.left, input.right), 0, 33, TimeUnit.MILLISECONDS);

        // Tick prediction + repaint ~60 FPS        
        AtomicLong last = new AtomicLong(System.nanoTime());
        Timer renderTimer = new Timer(16, ev -> {
            long now = System.nanoTime();
            double dt = Math.max(0, (now - last.getAndSet(now)) / 1_000_000_000.0);
            model.tickLocalPrediction(dt, input.up, input.down, input.left, input.right);
            panel.repaint(); // KHÔNG cast e.getSource() thành JComponent nữa
        });
        renderTimer.start();
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

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Vẽ lưới 32px cho dễ nhìn
            g2.setColor(new Color(255,255,255,28));
            for (int x = 0; x <= getWidth(); x += TILE_PX) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y <= getHeight(); y += TILE_PX) g2.drawLine(0, y, getWidth(), y);

            String you = model.you();

            // Lấy snapshot nội suy theo TILE
            var ents = model.sampleForRender();

            // Overlay vị trí predicted của "you" (cho cảm giác ngay lập tức)
            var myPred = model.getPredictedYou();
            if (you != null && myPred != null) {
                ents.put(you, myPred);
            }

            if (ents.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("waiting for state...", 20, 20);
                return;
            }

            int rPx = 10;
            ents.forEach((id, pos) -> {
                int px = toPx(pos.x);
                int py = toPx(pos.y);
                g2.setColor(id.equals(you) ? Color.GREEN : Color.CYAN);
                g2.fillOval(px - rPx, py - rPx, rPx*2, rPx*2);
                g2.setColor(Color.WHITE);
                g2.drawString(id, px + 12, py - 12);
            });

            // Debug góc dưới
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("tile="+TILE_PX+"px; ents="+ents.size(), 10, getHeight()-10);
        }
    }
}
