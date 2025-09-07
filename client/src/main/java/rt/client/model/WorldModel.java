package rt.client.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Trạng thái client: id -> (x,y). Không khóa nặng, đủ dùng cho demo. */
public class WorldModel {
    public static final class Pos { public volatile double x, y; }
    private final Map<String, Pos> ents = new ConcurrentHashMap<>();
    private volatile String you;

    public void setYou(String id) { this.you = id; }
    public String you() { return you; }

    /** Áp state JSON dạng {type:"state", ents:{id:{x,y}}}. */
    @SuppressWarnings("unchecked")
    public void applyState(Map<String, Object> root) {
        Object eobj = root.get("ents");
        if (!(eobj instanceof Map)) return;
        Map<String, Object> em = (Map<String, Object>) eobj;
        for (var entry : em.entrySet()) {
            String id = entry.getKey();
            Object v = entry.getValue();
            if (!(v instanceof Map)) continue;
            Map<String, Object> xy = (Map<String, Object>) v;
            double x = ((Number) xy.getOrDefault("x", 0)).doubleValue();
            double y = ((Number) xy.getOrDefault("y", 0)).doubleValue();
            ents.computeIfAbsent(id, k -> new Pos());
            Pos p = ents.get(id);
            p.x = x; p.y = y;
        }
    }

    public Map<String, Pos> snapshot() { return ents; }
}
