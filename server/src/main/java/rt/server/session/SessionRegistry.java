package rt.server.session;

import io.netty.channel.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private static final ObjectMapper OM = new ObjectMapper();
    private final Map<Channel, Session> byCh = new ConcurrentHashMap<>();
    private final Map<String, Session> byId = new ConcurrentHashMap<>();

    public void attach(Session s){ byCh.put(s.ch, s); byId.put(s.playerId, s); }
    public void detach(Channel ch){ var s = byCh.remove(ch); if (s!=null) byId.remove(s.playerId); }
    Session byChannel(Channel ch){ return byCh.get(ch); }
    public Iterable<Session> all(){ return byId.values(); }

    public static class Session {
        final Channel ch; public final String playerId;
        public volatile double x = 100; // server là nguồn sự thật
		public volatile double y = 100;
        public Session(Channel ch, String id){ this.ch=ch; this.playerId=id; }
        public void send(Object obj){
            try { ch.writeAndFlush(new TextWebSocketFrame(OM.writeValueAsString(obj))); }
            catch (Exception e){ e.printStackTrace(); }
        }
    }
}

