package rt.server.game.loop;

import rt.server.input.InputQueue;
import rt.server.world.World;

/** Vòng lặp tick cố định 60 TPS: luôn chạy đều đặn, không phụ thuộc FPS client. */
public class GameLoop implements Runnable {
    private final World world; 
    private final InputQueue inputs; 
    private final SnapshotBuffer snaps;
    private final double dt; // thời gian mỗi tick (s)
    
    public GameLoop(World w, InputQueue iq, SnapshotBuffer sb, double tps){
        this.world=w; this.inputs=iq; this.snaps=sb; this.dt = 1.0 / tps;
    }
    
    @Override public void run() {
        long prev = System.nanoTime();
        double acc = 0, ns = 1_000_000_000.0 * dt; long tick = 0;
        while (true){
            long now = System.nanoTime(); acc += (now - prev) / ns; prev = now;
            while (acc >= 1.0){
                var batch = inputs.drain();   // 1) hút input đã nhận
                world.applyInputs(batch);     // 2) áp dụng vào state
                world.step(dt);               // 3) mô phỏng 1 tick
                snaps.write(world.capture(tick++)); // 4) lưu snapshot cho streamer
                acc -= 1.0;
            }
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}

