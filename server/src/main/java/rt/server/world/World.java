package rt.server.world;

import rt.server.session.SessionRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side authoritative world. */
public class World {
    private final SessionRegistry sessions;

    private static final double SPEED = 120.0; // pixels per second
    private static final double W = 800, H = 600;

    /** Simple state per player. You can expand later. */
    private static final class P { double x, y; }
    private final Map<String, P> players = new ConcurrentHashMap<>();

    public World(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    /** Ensure player exists; called before applying inputs. */
    private P ensure(String id) {
        return players.computeIfAbsent(id, k -> {
            P p = new P();
            p.x = 100; p.y = 100;
            return p;
        });
    }

    /** Apply a directional input for one player. */
    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right) {
        P p = ensure(playerId);
        // store desired direction on the fly by setting velocity now (stateless approach)
        double vx = (right ? 1 : 0) - (left ? 1 : 0);
        double vy = (down  ? 1 : 0) - (up   ? 1 : 0);
        // normalize (optional)
        double len = Math.hypot(vx, vy);
        if (len > 0) { vx /= len; vy /= len; }
        // stash velocity in x/y delta using SPEED on next step (we do it directly in step by dt)
        // Here we just store as a temporary "impulse" by placing on a thread-local—simpler: update position immediately by small dt? No, do it in step: mark vel
        // For simplicity: cache velocities into a map (attach to P)
        // To keep P minimal, we update position immediately in step using lastInput map:
        lastInput.put(playerId, new Dir(vx, vy));
    }

    private static final class Dir { final double x, y; Dir(double x,double y){ this.x=x; this.y=y; } }
    private final Map<String, Dir> lastInput = new ConcurrentHashMap<>();

    /** Advance physics by dt seconds. */
    public void step(double dt) {
        // apply last inputs
        lastInput.forEach((id, dir) -> {
            P p = ensure(id);
            p.x += dir.x * SPEED * dt;
            p.y += dir.y * SPEED * dt;
            // clamp
            if (p.x < 0) p.x = 0; if (p.y < 0) p.y = 0;
            if (p.x > W) p.x = W; if (p.y > H) p.y = H;
        });
    }

    /** Fill a network map: id -> {x:..., y:...} */
    public void copyForNetwork(Map<String, Map<String, Object>> out) {
        players.forEach((id, p) -> {
            out.put(id, Map.of("x", p.x, "y", p.y));
        });
        // ensure sessions also exist in players (new joiners)
        sessions.all().forEach(s -> players.computeIfAbsent(s.playerId, k -> {
            P p = new P(); p.x = 100; p.y = 100; return p;
        }));
    }
}
