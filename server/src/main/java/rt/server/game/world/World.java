package rt.server.game.world;

import vq.common.Packets;
import rt.server.InputEvent;
import rt.server.session.SessionRegistry;

import java.util.HashMap;
import java.util.Map;

/** Trạng thái game ở server. Chỉ server được quyền cập nhật. */
public class World {
    private final SessionRegistry sessions;
    private final Map<String, Player> players = new HashMap<>();

    private final double speed = 120.0; // px/s (tăng/giảm để cảm giác mượt)
    private final int W = 1280, H = 720; // ranh giới tạm

    public World(SessionRegistry sessions){ this.sessions = sessions; }

    /** Áp dụng input batch cho tick hiện tại (set hướng di chuyển). */
    void applyInputs(Iterable<InputEvent> batch){
        for (var e : batch){
            var p = players.computeIfAbsent(e.playerId(), id -> new Player());
            p.ax = e.ax(); p.ay = e.ay(); p.lastSeq = e.seq();
        }
    }

    /** Bước mô phỏng: v = a*speed; x += v*dt; clamp biên; cập nhật về Session. */
    void step(double dt){
        // đảm bảo có Player tương ứng cho mọi Session
        for (var s : sessions.all()){
            players.computeIfAbsent(s.playerId, id -> new Player()).syncFromSession(s);
        }
        for (var ent : players.entrySet()){
            var st = ent.getValue();
            st.vx = st.ax * speed;
            st.vy = st.ay * speed;
            st.x += st.vx * dt;
            st.y += st.vy * dt;
            // va biên đơn giản
            if (st.x < 0) st.x = 0; if (st.y < 0) st.y = 0;
            if (st.x > W) st.x = W; if (st.y > H) st.y = H;
        }
        // copy “sự thật” về Session (để streamer push cho client)
        for (var s : sessions.all()){
            var st = players.get(s.playerId);
            if (st!=null){ s.x = st.x; s.y = st.y; }
        }
    }

    /** Lấy snapshot gửi cho client. */
    Snapshot capture(long tick){
        var ents = new HashMap<String, Packets.S2CState.Player>();
        for (var s : sessions.all()){
            var p = new Packets.S2CState.Player();
            p.x = s.x; p.y = s.y; // những người khác: chỉ cần x,y
            ents.put(s.playerId, p);
        }
        return new Snapshot(tick, ents);
    }

    static class Player {
        double x=100, y=100, vx, vy; int ax, ay, lastSeq;
        void syncFromSession(SessionRegistry.Session s){ this.x = s.x; this.y = s.y; }
    }

    /** Gói ảnh chụp thế giới (đơn giản). */
    record Snapshot(long tick, Map<String, Packets.S2CState.Player> ents) {}
}

