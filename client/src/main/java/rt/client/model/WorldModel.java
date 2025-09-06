package rt.client.model;

import java.util.*;
import java.util.function.Consumer;

/** Giữ trạng thái local và nội suy người chơi khác cho mượt. */
public class WorldModel {
    public static class Player { public double x, y; public double tx, ty; }

    private String myId;
    private final Player me = new Player();
    private final Map<String, Player> others = new HashMap<>();

    public void setMyId(String id){ this.myId = id; }
    public Player getMe(){ return me; }
    public int count(){ return 1 + others.size(); }

    /** Server gửi state: cập nhật yourself + targets cho others. */
    public synchronized void applyStateFromServer(long tick, Map<String, Player> ents){
        // cập nhật target cho others
        for (var e : ents.entrySet()){
            var id = e.getKey();
            if (id.equals(myId)) continue;
            var srv = e.getValue();
            var loc = others.computeIfAbsent(id, k -> new Player());
            loc.tx = srv.x; loc.ty = srv.y;
            if (Double.isNaN(loc.x) || (loc.x==0 && loc.y==0)) {
                loc.x = loc.tx; loc.y = loc.ty; // lần đầu "đặt" luôn
            }
        }
        // dọn người biến mất (tùy chọn)
        others.keySet().removeIf(id -> !ents.containsKey(id));
    }

    public synchronized void updateMe(double x, double y){ me.x = x; me.y = y; }

    /** Nội suy others mỗi frame. */
    public synchronized void updateInterpolation(double dt){
        double lerp = 10.0 * dt; // hệ số nội suy
        for (var p : others.values()){
            p.x += (p.tx - p.x) * lerp;
            p.y += (p.ty - p.y) * lerp;
        }
    }

    public synchronized void forEachOther(Consumer<Player> fn){
        for (var p : others.values()) fn.accept(p);
    }
}
