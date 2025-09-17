package rt.server.session;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private final Map<Channel, Session> byCh = new ConcurrentHashMap<>();
    private final Map<String, Session> byId = new ConcurrentHashMap<>();

    /** Giữ API cũ. */
    public Session attach(Session s) {
        byCh.put(s.ch, s);
        byId.put(s.playerId, s);
        return s;
    }

    /** Overload tiện dụng: tự tạo Session từ Channel và trả về. */
    public Session attach(Channel ch) {
        String id = ch.id().asShortText();
        Session s = new Session(ch, id);
        byCh.put(ch, s);
        byId.put(id, s);
        return s;
    }

    public void detach(Channel ch) {
        Session s = byCh.remove(ch);
        if (s != null) byId.remove(s.playerId);
    }

    public Session byChannel(Channel ch) { return byCh.get(ch); }
    public Iterable<Session> all() { return byId.values(); }

    public static class Session {
        public final Channel ch;
        public final String playerId;
        public volatile double x = 100, y = 100;

        public final java.util.concurrent.atomic.AtomicLong droppedInputs =
                new java.util.concurrent.atomic.AtomicLong();
        public final java.util.concurrent.atomic.AtomicLong streamerSkips =
                new java.util.concurrent.atomic.AtomicLong();

        public Session(Channel ch, String id) { this.ch = ch; this.playerId = id; }

        public void send(Object obj) {
            try {
                ch.writeAndFlush(new TextWebSocketFrame(
                        rt.common.net.Jsons.OM.writeValueAsString(obj)));
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
