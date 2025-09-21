package rt.server.game.input;

import rt.server.world.World;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class InputQueue {
    /** Trạng thái phím bất biến + equals/hashCode để so sánh nhanh. */
    public static final class Keys {
        public final boolean up, down, left, right;
        public Keys(boolean up, boolean down, boolean left, boolean right) {
            this.up = up; this.down = down; this.left = left; this.right = right;
        }
        @Override public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof Keys k)) return false;
            return up==k.up && down==k.down && left==k.left && right==k.right;
        }
        @Override public int hashCode(){ return Objects.hash(up,down,left,right); }
        public static final Keys EMPTY = new Keys(false,false,false,false);
    }

    // last input được CHẤP NHẬN theo rate-limit (per player)
    private final ConcurrentHashMap<String, Keys> latestAccepted = new ConcurrentHashMap<>();
    // last input đã APPLY xuống world (để tránh apply trùng)
    private final ConcurrentHashMap<String, Keys> lastApplied    = new ConcurrentHashMap<>();
    // seq đã CHẤP NHẬN gần nhất (phục vụ thống kê/ack nếu cần)
    private final ConcurrentHashMap<String, Integer> lastAcceptedSeq = new ConcurrentHashMap<>();
    // rate-limit clock (per player)
    private static final long MIN_INTERVAL_NS = 16_000_000L; // ~60/s
    private final ConcurrentHashMap<String, Long> lastAcceptNs = new ConcurrentHashMap<>();
    // suppress tạm thời (per player): trước thời điểm này -> ép về Keys.EMPTY
    private final ConcurrentHashMap<String, Long> suppressUntilNs = new ConcurrentHashMap<>();

    /** Push từ WS: chỉ nhận nếu qua mốc 16ms và khác lần trước; còn lại drop yên lặng. */
    public void offer(String playerId, int seq, Map<String, Boolean> keys) {
        if (playerId == null || keys == null) return;

        final Keys incoming = new Keys(
                keys.getOrDefault("up", false),
                keys.getOrDefault("down", false),
                keys.getOrDefault("left", false),
                keys.getOrDefault("right", false)
        );

        // nếu giống hệt gói đã chấp nhận gần nhất -> bỏ qua
        Keys prev = latestAccepted.get(playerId);
        if (incoming.equals(prev)) {
            // vẫn cập nhật seq để không báo trễ, nhưng không thay đổi trạng thái
            lastAcceptedSeq.put(playerId, Math.max(seq, lastAcceptedSeq.getOrDefault(playerId, 0)));
            return;
        }

        long now = System.nanoTime();
        long last = lastAcceptNs.getOrDefault(playerId, 0L);
        if (now - last < MIN_INTERVAL_NS) {
            // chưa tới mốc 16ms -> drop yên lặng
            return;
        }

        // accept
        lastAcceptNs.put(playerId, now);
        latestAccepted.put(playerId, incoming);
        lastAcceptedSeq.put(playerId, seq);
    }

    /** World đọc trạng thái phím mỗi tick; chỉ apply khi khác lần trước. */
    public void applyToWorld(World world) {
        long now = System.nanoTime();
        latestAccepted.forEach((id, k) -> {
            // suppress tạm thời (ví dụ sau teleport)
            long until = suppressUntilNs.getOrDefault(id, 0L);
            Keys eff = (now < until) ? Keys.EMPTY : k;

            // tránh apply trùng
            Keys was = lastApplied.get(id);
            if (eff.equals(was)) return;

            world.applyInput(id, eff.up, eff.down, eff.left, eff.right);
            lastApplied.put(id, eff);
        });
    }

    /** Seq cuối đã CHẤP NHẬN (không phải mọi gói gửi tới). */
    public int lastProcessedSeq(String playerId) {
        return lastAcceptedSeq.getOrDefault(playerId, 0);
    }

    /** Ép im lặng input người chơi trong quietMillis (ví dụ sau teleport/menu). */
    public void suppress(String playerId, long quietMillis) {
        if (playerId == null) return;
        long until = System.nanoTime() + quietMillis * 1_000_000L;
        suppressUntilNs.put(playerId, until);
        // cũng đẩy state rỗng để dừng ngay frame hiện tại
        latestAccepted.put(playerId, Keys.EMPTY);
    }

    /** Xoá trạng thái player. */
    public void remove(String playerId) {
        if (playerId == null) return;
        latestAccepted.remove(playerId);
        lastApplied.remove(playerId);
        lastAcceptedSeq.remove(playerId);
        lastAcceptNs.remove(playerId);
        suppressUntilNs.remove(playerId);
    }
}
