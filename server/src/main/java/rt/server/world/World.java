package rt.server.world;

import rt.common.net.dto.EntityState;
import rt.common.net.dto.StateS2C;
import rt.server.session.SessionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Trạng thái game (đơn vị tile). Server là nguồn sự thật. */
public class World {
    private final SessionRegistry sessions;

    // Vị trí authoritative của player (đơn vị tile)
    private static final class P { double x, y; }
    private final Map<String, P> players = new ConcurrentHashMap<>();

    // Input “mới nhất” mỗi player (đã chuẩn hoá)
    private static final class Dir { final double x,y; Dir(double x,double y){this.x=x;this.y=y;} }
    private final Map<String, Dir> lastInput = new ConcurrentHashMap<>();

    // Tốc độ: tile/giây
    private static final double SPEED = 3.0;

    // Bản đồ
    private volatile TileMap map = TileMap.demo();

    public World(SessionRegistry sessions) { this.sessions = sessions; }

    public void setMap(TileMap m){ this.map = Objects.requireNonNull(m); }
    public TileMap map(){ return map; }

    private P ensure(String id){
        return players.computeIfAbsent(id, k -> {
            P p = new P();
            p.x = 3; p.y = 3; // spawn mặc định
            return p;
        });
    }

    /** Client gửi input → lưu vector chuẩn hoá. */
    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right) {
        double vx = (right ? 1 : 0) - (left ? 1 : 0);
        double vy = (down  ? 1 : 0) - (up   ? 1 : 0);
        double len = Math.hypot(vx, vy);
        if (len > 0) { vx/=len; vy/=len; }
        lastInput.put(playerId, new Dir(vx, vy));
        ensure(playerId); // tạo nếu chưa có
    }

    /** 1 tick logic theo dt (giây). Collision theo tile với sweep trục X rồi Y. */
    public void step(double dt) {
        TileMap m = this.map;
        lastInput.forEach((id, dir) -> {
            P p = ensure(id);
            double nx = p.x + dir.x * SPEED * dt;
            double ny = p.y + dir.y * SPEED * dt;

            // Clamp map bounds (điểm player nằm trong map)
            nx = Math.max(0, Math.min(nx, m.w - 1e-3));
            ny = Math.max(0, Math.min(ny, m.h - 1e-3));

            // Sweep X
            int tx = (int)Math.floor(nx);
            int ty = (int)Math.floor(p.y); // Y tạm giữ
            if (!m.blocked(tx, ty)) p.x = nx;

            // Sweep Y
            tx = (int)Math.floor(p.x);
            ty = (int)Math.floor(ny);
            if (!m.blocked(tx, ty)) p.y = ny;

            // Phản chiếu sang session (để streamer đọc nếu đang dùng Session.x/y)
            for (var s : sessions.all()) {
                if (s.playerId.equals(id)) { s.x = p.x; s.y = p.y; break; }
            }
        });
    }

    /** Ảnh chụp nhất quán cho streamer. */
    public StateS2C capture(long tick){
        Map<String, EntityState> ents = new HashMap<>(players.size());
        players.forEach((id, p) -> ents.put(id, new EntityState(p.x, p.y)));
        return new StateS2C(tick, System.currentTimeMillis(), ents);
    }

    // (Giữ nguyên nếu nơi khác đang dùng)
    public void copyForNetwork(Map<String, Map<String, Object>> out) {
        players.forEach((id, p) -> out.put(id, Map.of("x", p.x, "y", p.y)));
    }

    // tiện cho test
    public double[] pos(String id){ P p = players.get(id); return p==null? null : new double[]{p.x, p.y}; }
    
    public boolean teleport(String id, double x, double y) {
        TileMap m = this.map;
        if (x < 0 || y < 0 || x >= m.w || y >= m.h) return false;
        P p = ensure(id);
        int tx = (int)Math.floor(x), ty = (int)Math.floor(y);
        if (m.blocked(tx, ty)) return false;
        p.x = x; p.y = y;
        sessions.all().forEach(s -> { if (s.playerId.equals(id)) { s.x = x; s.y = y; } });
        return true;
    }

    public boolean reloadMap(String resourcePath) {
        try {
            setMap(TileMap.loadResource(resourcePath));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
