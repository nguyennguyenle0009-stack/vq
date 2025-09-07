package rt.client.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldModel {
    public static final class Pos { public double x, y; }
    public static final class Snapshot {
        public final long ts; // server timestamp ms
        public final Map<String, Pos> ents;
        Snapshot(long ts, Map<String, Pos> ents){ this.ts = ts; this.ents = ents; }
    }

    private volatile String you;
    private final Deque<Snapshot> buffer = new ArrayDeque<>(); // giữ ~ 1–2s snapshot
    private static final int MAX_BUF = 60;          // đủ cho ~5s ở 12Hz
    //private static final long INTERP_DELAY = 100;   // ms – độ trễ để nội suy mượt

    public void setYou(String id){ this.you = id; }
    public String you(){ return you; }

    @SuppressWarnings("unchecked")
    public void applyState(Map<String, Object> root) {
        Object entsObj = root.get("ents");
        Object tsObj = root.get("ts");
        if (!(entsObj instanceof Map) || !(tsObj instanceof Number)) return;
        long ts = ((Number) tsObj).longValue();

        Map<String, Object> em = (Map<String, Object>) entsObj;
        Map<String, Pos> ents = new HashMap<>(em.size());
        for (var e : em.entrySet()) {
            Map<String, Object> xy = (Map<String, Object>) e.getValue();
            Pos p = new Pos();
            p.x = ((Number) xy.getOrDefault("x", 0)).doubleValue();
            p.y = ((Number) xy.getOrDefault("y", 0)).doubleValue();
            ents.put(e.getKey(), p);
        }
        synchronized (buffer) {
            buffer.addLast(new Snapshot(ts, ents));
            while (buffer.size() > MAX_BUF) buffer.removeFirst();
        }
    }

    /** Lấy ảnh thế giới tại thời điểm renderTime (ms) bằng nội suy tuyến tính. */
    public Map<String, Pos> sample(long renderTimeMs) {
        Snapshot a = null, b = null;
        synchronized (buffer) {
            if (buffer.size() < 1) return Map.of();
            // We want a.ts <= t <= b.ts. Nếu không tìm thấy, trả snapshot gần nhất.
            for (Snapshot s : buffer) {
                if (s.ts <= renderTimeMs) a = s;
                if (s.ts >= renderTimeMs) { b = s; break; }
            }
            if (a == null) a = buffer.peekFirst();
            if (b == null) b = buffer.peekLast();
        }
        if (a == null || b == null) return Map.of();

        if (a == b) return a.ents; // đúng 1 mốc

        double t = (renderTimeMs - a.ts) / (double) (b.ts - a.ts);
        if (t < 0) t = 0; if (t > 1) t = 1;

        // nội suy từng entity
        Map<String, Pos> out = new ConcurrentHashMap<>();
        Set<String> ids = new HashSet<>();
        ids.addAll(a.ents.keySet());
        ids.addAll(b.ents.keySet());
        for (String id : ids) {
            Pos pa = a.ents.get(id), pb = b.ents.get(id);
            if (pa == null) pa = pb;
            if (pb == null) pb = pa;
            if (pa == null) continue;
            Pos p = new Pos();
            p.x = pa.x + (pb.x - pa.x) * t;
            p.y = pa.y + (pb.y - pa.y) * t;
            out.put(id, p);
        }
        return out;
    }

    /** Dùng khi muốn vẽ ngay 1 chấm sau hello. */
    public void spawn(String id, double x, double y) {
        Map<String, Pos> ents = new HashMap<>();
        Pos p = new Pos(); p.x = x; p.y = y; ents.put(id, p);
        synchronized (buffer) {
            long now = System.currentTimeMillis();
            buffer.addLast(new Snapshot(now, ents));
            while (buffer.size() > MAX_BUF) buffer.removeFirst();
        }
    }
}
