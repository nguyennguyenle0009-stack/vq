package rt.server.game.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.session.SessionRegistry;
import rt.server.session.SessionRegistry.Session;
import rt.server.world.World;

import java.util.HashMap;
import java.util.Map;

/** Periodically send state snapshots to all sessions. */
public class SnapshotStreamer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SnapshotStreamer.class);

    private final SessionRegistry sessions;
    private final World world;
    private final int hz;

    private long tick = 0;

    public SnapshotStreamer(SessionRegistry sessions, World world, int hz) {
        this.sessions = sessions; this.world = world; this.hz = hz;
    }

    @Override public void run() {
        long periodMs = 1000L / Math.max(1, hz);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<String, Map<String, Object>> ents = new HashMap<>();
                world.copyForNetwork(ents);

                Map<String,Object> state = Map.of(
                    "type", "state",
                    "tick", ++tick,
                    "ts", System.currentTimeMillis(),
                    "ents", ents
                );

                for (Session s : sessions.all()) s.send(state);

                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("snapshot error", e);
            }
        }
    }
}
