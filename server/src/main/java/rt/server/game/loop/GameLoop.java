package rt.server.game.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.input.InputQueue;
import rt.server.input.InputQueue.InputEvent;
import rt.server.world.World;

import java.util.ArrayList;
import java.util.List;

/** Fixed-timestep loop at given TPS. */
public class GameLoop implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private final World world;
    private final InputQueue inputs;
    private final double tps;
    private final double dt; // seconds

    private final List<InputEvent> batch = new ArrayList<>(128);

    public GameLoop(World world, InputQueue inputs, double tps) {
        this.world = world;
        this.inputs = inputs;
        this.tps = tps;
        this.dt = 1.0 / tps;
    }

    @Override public void run() {
        long nsPerTick = (long) (1_000_000_000L / tps);
        long next = System.nanoTime();

        while (!Thread.currentThread().isInterrupted()) {
            // 1) Process inputs
            batch.clear();
            inputs.drainAllTo(batch);
            for (InputEvent e : batch) {
                world.applyInput(e.playerId, e.up, e.down, e.left, e.right);
            }

            // 2) Step world
            world.step(dt);

            // 3) Sleep until next tick
            next += nsPerTick;
            long sleep = next - System.nanoTime();
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000L, (int)(sleep % 1_000_000L)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } else {
                // behind schedule; catch up
                next = System.nanoTime();
            }
        }
    }
}
