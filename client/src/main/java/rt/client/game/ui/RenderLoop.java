package rt.client.game.ui;

import javax.swing.*;

/** Vòng lặp render 60 FPS, chạy thread nền và gọi repaint() an toàn (EDT sẽ vẽ). */
public class RenderLoop implements Runnable {
    private final JFrame frame;
    private final JComponent component;
    private volatile boolean running = true;
    private Thread thread;

    public RenderLoop(JFrame frame, JComponent component) {
        this.frame = frame;
        this.component = component;
    }

    public void start() {
        if (thread != null) return;
        thread = new Thread(this, "render-loop");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(200); } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void run() {
        final long frameNs = 16_666_667L; // ~60 FPS
        long next = System.nanoTime();
        while (running && frame.isDisplayable()) {
            long now = System.nanoTime();
            if (now < next) {
                long sleepMs = Math.max(0, (next - now) / 1_000_000);
                if (sleepMs > 0) {
                    try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
                }
                continue;
            }
            component.repaint(); // schedule vẽ trên EDT
            next += frameNs;
        }
    }
}
