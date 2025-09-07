package rt.server.input;

import java.util.Map;
import java.util.Queue;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Thread-safe queue: WS thread offers → GameLoop drains. */
public class InputQueue {

    /** One input of a player at sequence N. */
    public static final class InputEvent {
        public final String playerId;
        public final int seq;
        public final boolean up, down, left, right;
        public InputEvent(String playerId, int seq, boolean up, boolean down, boolean left, boolean right) {
            this.playerId = playerId; this.seq = seq;
            this.up = up; this.down = down; this.left = left; this.right = right;
        }
    }

    private final Queue<InputEvent> q = new ConcurrentLinkedQueue<>();
    /** Last accepted seq per player (drop duplicates/out-of-order). */
    private final Map<String, Integer> lastSeqSeen = new ConcurrentHashMap<>();

    /** Called by WS: push a new input. Keys may miss fields ⇒ treated as false. */
    public void offer(String playerId, int seq, Map<String, Boolean> keys) {
        if (playerId == null || keys == null) return;

        lastSeqSeen.compute(playerId, (id, prev) -> {
            if (prev != null && seq <= prev) return prev; // ignore stale/dup
            boolean up    = Boolean.TRUE.equals(keys.get("up"));
            boolean down  = Boolean.TRUE.equals(keys.get("down"));
            boolean left  = Boolean.TRUE.equals(keys.get("left"));
            boolean right = Boolean.TRUE.equals(keys.get("right"));
            q.add(new InputEvent(playerId, seq, up, down, left, right));
            return seq;
        });
    }

    /** Called by GameLoop each tick: drain up to max items into out. Returns drained count. */
    public int drainTo(List<InputEvent> out, int max) {
        int n = 0;
        for (; n < max; n++) {
            InputEvent e = q.poll();
            if (e == null) break;
            out.add(e);
        }
        return n;
    }

    /** Convenience: drain all. */
    public int drainAllTo(List<InputEvent> out) { return drainTo(out, Integer.MAX_VALUE); }
}
