package rt.server.game.loop;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.session.SessionRegistry;
import rt.server.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class SnapshotStreamer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SnapshotStreamer.class);

    private final SessionRegistry sessions;
    private final World world;
    private final int hz;
    private volatile long tick = 0;

    // Mỗi channel có 1 slot pending state
    private final ConcurrentHashMap<Channel, AtomicReference<Map<String, Object>>> pending = new ConcurrentHashMap<>();

    public SnapshotStreamer(SessionRegistry sessions, World world, int hz) {
        this.sessions = sessions;
        this.world = world;
        this.hz = Math.max(1, hz);
    }

    @Override
    public void run() {
        long periodMs = 1000L / hz;
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

                // ghi đè state mới nhất vào slot của từng channel
                sessions.all().forEach(s ->
                        pending.computeIfAbsent(s.ch, k -> new AtomicReference<>()).set(state));

                // gửi nếu kênh ghi được
                sessions.all().forEach(s -> {
                    Channel ch = s.ch;
                    AtomicReference<Map<String, Object>> slot = pending.get(ch);
                    if (slot == null) return;
                    if (ch.isActive() && ch.isWritable()) {
                        Map<String, Object> msg = slot.getAndSet(null);
                        if (msg != null) s.send(msg);
                    }
                });

                // ping ~10s
                if (tick % (hz * 10) == 0) {
                    Map<String,Object> ping = Map.of("type","ping","ts",System.currentTimeMillis());
                    sessions.all().forEach(s -> s.send(ping));
                }

                Thread.sleep(periodMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("snapshot error", e);
            }
        }
    }
}
