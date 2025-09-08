package rt.server.game.loop;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.common.net.dto.EntityState;
import rt.common.net.dto.PingS2C;
import rt.common.net.dto.StateS2C;
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

    // Mỗi channel có 1 slot chốt "state mới nhất"
    private final ConcurrentHashMap<Channel, AtomicReference<StateS2C>> pending = new ConcurrentHashMap<>();

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
                // 1) Lấy raw state (Map x,y) từ world và convert sang DTO EntityState
                Map<String, Map<String, Object>> raw = new HashMap<>();
                world.copyForNetwork(raw);
                Map<String, EntityState> ents = new HashMap<>(raw.size());
                raw.forEach((id, v) -> {
                    double x = ((Number)v.getOrDefault("x", 0)).doubleValue();
                    double y = ((Number)v.getOrDefault("y", 0)).doubleValue();
                    ents.put(id, new EntityState(x, y));
                });

                StateS2C state = new StateS2C(++tick, System.currentTimeMillis(), ents);

                // 2) Ghi đè state mới nhất cho từng channel
                sessions.all().forEach(s ->
                        pending.computeIfAbsent(s.ch, k -> new AtomicReference<>()).set(state));

                // 3) Drain nếu channel ghi được
                sessions.all().forEach(s -> {
                    Channel ch = s.ch;
                    AtomicReference<StateS2C> slot = pending.get(ch);
                    if (slot == null) return;
                    if (ch.isActive() && ch.isWritable()) {
                        StateS2C msg = slot.getAndSet(null);
                        if (msg != null) s.send(msg);
                    }
                });

                // 4) Ping ~10s
                if (tick % (hz * 10) == 0) {
                    PingS2C ping = new PingS2C(System.currentTimeMillis());
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
