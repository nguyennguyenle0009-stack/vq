package rt.server.game.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rt.server.game.input.InputQueue;
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
        final long stepNs = Math.round(1_000_000_000.0 / tps); // ~16_666_667 ns
        long next = System.nanoTime();

        final int MAX_CATCHUP_STEPS = 2; // tránh spiral of death

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.nanoTime();
                if (now < next) {
                    long sleepMs = Math.max(0, (next - now) / 1_000_000);
                    if (sleepMs > 0) Thread.sleep(sleepMs);
                    continue;
                }

                int steps = 0;
                // làm nhiều step nếu bị trễ, nhưng tối đa 3 step để không đốt CPU
                while (now >= next && steps < MAX_CATCHUP_STEPS) {
                    // 1) áp input mới nhất
                    inputs.applyToWorld(world);
                    // 2) tick logic
                    world.step(dt);

                    next += stepNs;
                    steps++;
                    now = System.nanoTime();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.warn("game loop error", t);
            }
        }
    }

}
