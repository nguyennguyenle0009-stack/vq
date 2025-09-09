package rt.server.game.loop;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.common.net.dto.StateS2C;
import rt.server.session.SessionRegistry;
import rt.server.world.World;

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

    @Override public void run() {
    	
        long periodMs = 1000L / hz;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StateS2C state = world.capture(++tick);

                sessions.all().forEach(s -> {
                    var ref = pending.computeIfAbsent(s.ch, k -> new java.util.concurrent.atomic.AtomicReference<rt.common.net.dto.StateS2C>());
                    var prev = ref.getAndSet(state);
                    if (prev != null) s.streamerSkips.incrementAndGet();
                });

                sessions.all().forEach(s -> {
                    Channel ch = s.ch;
                    AtomicReference<StateS2C> slot = pending.get(ch);
                    if (slot == null) return;
                    if (ch.isActive() && ch.isWritable()) {
                        StateS2C msg = slot.getAndSet(null);
                        if (msg != null) s.send(msg);
                    }
                });

                // Sau đoạn gửi state, mỗi 1 giây (tick % hz == 0) gửi DevStatsS2C:
                if (tick % hz == 0) { // ~1s
                    int entsCount = state.ents().size();
                    long ts = System.currentTimeMillis();
                    sessions.all().forEach(s -> {
                        boolean writable = s.ch.isWritable();
                        int dropped = (int) s.droppedInputs.getAndSet(0);   // reset mỗi giây
                        int skips   = (int) s.streamerSkips.getAndSet(0);   // reset mỗi giây
                        s.send(new rt.common.net.dto.DevStatsS2C(tick, ts, entsCount, dropped, skips, writable));
                    });
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
