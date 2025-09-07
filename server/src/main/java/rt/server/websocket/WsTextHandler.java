package rt.server.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.session.SessionRegistry;
import rt.server.session.SessionRegistry.Session;
import rt.server.input.InputQueue;

import java.util.Map;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WsTextHandler.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final SessionRegistry sessions;
    private final InputQueue inputs;

    public WsTextHandler(SessionRegistry sessions, InputQueue inputs) {
        this.sessions = sessions;
        this.inputs = inputs;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        String id = ctx.channel().id().asShortText();
        Session s = new Session(ctx.channel(), id);
        sessions.attach(s);
        log.info("channel added {}", id);
        s.send(Map.of("type", "hello", "you", id)); // client biết "you"
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        Map<String, Object> root = OM.readValue(text, new TypeReference<Map<String, Object>>(){});
        Object to = root.get("type");
        if (!(to instanceof String type)) { log.warn("missing type"); return; }

        Session s = sessions.byChannel(ctx.channel());
        if (s == null) { log.warn("no session for channel"); return; }

        switch (type) {
            case "hello" -> {
                // optional: lưu tên nếu bạn muốn
                Object name = root.get("name");
                log.info("hello from {} name={}", s.playerId, name);
                s.send(Map.of("type", "hello", "you", s.playerId));
            }
            case "input" -> {
                int seq = ((Number) root.getOrDefault("seq", 0)).intValue();
                @SuppressWarnings("unchecked")
                Map<String, Boolean> keys = (Map<String, Boolean>) root.getOrDefault("keys", Map.of());
                inputs.offer(s.playerId, seq, keys);               // << dùng đúng offer(...)
                s.send(Map.of("type", "ack", "seq", seq));          // trả ack
            }
            default -> log.warn("unknown type {}", type);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        sessions.detach(ctx.channel());
        log.info("channel removed {}", ctx.channel().id().asShortText());
    }
}
