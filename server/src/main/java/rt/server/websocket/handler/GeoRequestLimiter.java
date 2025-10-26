package rt.server.websocket.handler;

import rt.common.net.ErrorCodes;
import rt.common.net.dto.ErrorS2C;
import rt.server.session.SessionRegistry;

import java.util.concurrent.ConcurrentHashMap;

/** Limits geo_req frequency to protect expensive lookups. */
public final class GeoRequestLimiter {
    private final int maxPerWindow;
    private final long windowMs;
    private final long notifyCooldownMs;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        long windowStart;
        int count;
        long lastNotify;
    }

    public GeoRequestLimiter(int maxPerWindow, long windowMs, long notifyCooldownMs) {
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
        this.notifyCooldownMs = notifyCooldownMs;
    }

    public boolean allow(String playerId, SessionRegistry.Session session) {
        long now = System.currentTimeMillis();
        State state = states.computeIfAbsent(playerId, k -> new State());
        if (now - state.windowStart >= windowMs) {
            state.windowStart = now;
            state.count = 0;
        }
        state.count++;
        if (state.count <= maxPerWindow) {
            return true;
        }
        if (now - state.lastNotify >= notifyCooldownMs) {
            state.lastNotify = now;
            session.send(new ErrorS2C(ErrorCodes.RATE_LIMIT_GEO,
                    "Too many geo lookups (> " + maxPerWindow + "/s)"));
        }
        return false;
    }
}
