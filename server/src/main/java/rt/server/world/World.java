package rt.server.world;

import rt.server.session.SessionRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rt.common.game.Units.*;

/** Server-side authoritative world (đơn vị: TILE). */
public class World {
    private final SessionRegistry sessions;

    // World size & speed theo đơn vị tile
    private static final double W = WORLD_W_TILES;
    private static final double H = WORLD_H_TILES;
    private static final double SPEED = SPEED_TILES_PER_SEC; // tiles/s

    private static final class P { double x, y; }
    private final Map<String, P> players = new ConcurrentHashMap<>();

    public World(SessionRegistry sessions) { this.sessions = sessions; }

    private P ensure(String id) {
        return players.computeIfAbsent(id, k -> { var p = new P(); p.x = 3; p.y = 3; return p; });
    }

    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right) {
        double vx = (right ? 1 : 0) - (left ? 1 : 0);
        double vy = (down  ? 1 : 0) - (up   ? 1 : 0);
        double len = Math.hypot(vx, vy);
        if (len > 0) { vx/=len; vy/=len; }
        lastInput.put(playerId, new Dir(vx, vy));
        ensure(playerId); // tạo nếu chưa có
    }

    private static final class Dir { final double x,y; Dir(double x,double y){this.x=x;this.y=y;} }
    private final Map<String, Dir> lastInput = new ConcurrentHashMap<>();

    /** dt tính bằng giây; cập nhật theo tile. */
    public void step(double dt) {
        lastInput.forEach((id, dir) -> {
            P p = ensure(id);
            p.x += dir.x * SPEED * dt;
            p.y += dir.y * SPEED * dt;
            if (p.x < 0) p.x = 0; if (p.y < 0) p.y = 0;
            if (p.x > W) p.x = W; if (p.y > H) p.y = H;
            // (tuỳ chọn) phản chiếu sang Session nếu nơi khác đọc
            sessions.all().forEach(s -> { if (s.playerId.equals(id)) { s.x = p.x; s.y = p.y; }});
        });
    }

    /** Đổ state cho network: x,y theo tile (client sẽ * 32px để vẽ). */
    public void copyForNetwork(Map<String, Map<String, Object>> out) {
        // bảo đảm ai online cũng có entity
        sessions.all().forEach(s -> ensure(s.playerId));
        players.forEach((id, p) -> out.put(id, Map.of("x", p.x, "y", p.y)));
    }
}
