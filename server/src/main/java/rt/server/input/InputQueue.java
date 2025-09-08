package rt.server.input;

import rt.server.world.World;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InputQueue {
    public static final class Keys {
        public final boolean up, down, left, right;
        public Keys(boolean up, boolean down, boolean left, boolean right) {
            this.up = up; this.down = down; this.left = left; this.right = right;
        }
    }

    private final ConcurrentHashMap<String, Keys> latest = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastSeq = new ConcurrentHashMap<>();

    // ≤ 60/s → mỗi gói cách nhau tối thiểu ~16ms
    private static final long MIN_INTERVAL_NS = 16_000_000L;
    private final ConcurrentHashMap<String, Long> lastAcceptNs = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public void offer(String playerId, int seq, Map<String, Boolean> keys) {
        if (playerId == null || keys == null) return;

        // bỏ out-of-order theo seq
        lastSeq.compute(playerId, (k, prev) -> (prev == null || seq > prev) ? seq : prev);

        boolean up    = keys.getOrDefault("up",    false);
        boolean down  = keys.getOrDefault("down",  false);
        boolean left  = keys.getOrDefault("left",  false);
        boolean right = keys.getOrDefault("right", false);

        // luôn giữ trạng thái phím mới nhất (coalesce)
        latest.put(playerId, new Keys(up, down, left, right));

        // rate-limit theo time window (nếu muốn hard drop logic khác, đặt ở đây)
        long now = System.nanoTime();
        lastAcceptNs.compute(playerId, (k, prev) -> {
            if (prev == null || now - prev >= MIN_INTERVAL_NS) return now; // accept
            return prev; // drop (không cập nhật prev)
        });
    }

    /** World đọc trạng thái phím hiện tại mỗi tick. */
    public void applyToWorld(World world) {
        latest.forEach((id, k) -> world.applyInput(id, k.up, k.down, k.left, k.right));
    }

    public int lastProcessedSeq(String playerId) {
        return lastSeq.getOrDefault(playerId, 0);
    }
}
