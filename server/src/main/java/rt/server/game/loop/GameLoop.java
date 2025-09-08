package rt.server.game.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.input.InputQueue;
import rt.server.world.World;

/** Vòng lặp game authoritative (đơn vị tile). */
public class GameLoop implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private final World world;
    private final InputQueue inputs;
    private final int tps;
    private final double dt;

    public GameLoop(World world, InputQueue inputs, int tps) {
        this.world = world;
        this.inputs = inputs;
        this.tps = Math.max(1, tps);
        this.dt = 1.0 / this.tps;
    }

    @Override
    public void run() {
        final long stepNs = Math.round(1_000_000_000.0 / tps);
        long next = System.nanoTime();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.nanoTime();
                long behind = now - next;
                if (behind < 0) {
                    long sleepMs = Math.max(0, (-behind) / 1_000_000);
                    if (sleepMs > 0) Thread.sleep(sleepMs);
                    continue;
                }
                // 1) Áp input “mới nhất” của từng player
                inputs.applyToWorld(world);

                // 2) Cập nhật world theo dt (giây)
                world.step(dt);

                next += stepNs;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.warn("game loop error", t);
            }
        }
    }
}
