// WsTextHandler.java
package rt.server.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.session.SessionRegistry.Session;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WsTextHandler.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final SessionRegistry sessions;
    private final InputQueue inputs;

    // theo dõi heartbeat (không bắt buộc, dùng để debug)
    private final ConcurrentHashMap<String, Long> lastPong = new ConcurrentHashMap<>();

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
        s.send(Map.of("type", "hello", "you", id));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        Map<String, Object> root = OM.readValue(text, new TypeReference<Map<String, Object>>() {});
        Object to = root.get("type");
        if (!(to instanceof String type)) { log.warn("missing type"); return; }

        Session s = sessions.byChannel(ctx.channel());
        if (s == null) { log.warn("no session for channel"); return; }

        switch (type) {
            case "hello" -> {
                Object name = root.get("name");
                log.info("hello from {} name={}", s.playerId, name);
                s.send(Map.of("type", "hello", "you", s.playerId));
            }
            case "input" -> {
                int seq = ((Number) root.getOrDefault("seq", 0)).intValue();
                @SuppressWarnings("unchecked")
                Map<String, Boolean> keys = (Map<String, Boolean>) root.getOrDefault("keys", Map.of());
                inputs.offer(s.playerId, seq, keys);
                s.send(Map.of("type", "ack", "seq", seq));
            }
            case "pong" -> {
                long now = System.currentTimeMillis();
                long ts = root.get("ts") instanceof Number ? ((Number) root.get("ts")).longValue() : 0L;
                lastPong.put(s.playerId, now);
                long rtt = ts > 0 ? now - ts : -1;
                log.debug("pong {} rtt={}ms", s.playerId, rtt);
            }
            case "cping" -> {
                Object ns = root.get("ns"); // nano của client
                s.send(Map.of("type","cpong","ns", ns)); // trả nguyên lại ns để client tính RTT
            }
            default -> log.warn("unknown type {}", type);
        }
    }

    // client đóng → dọn dẹp nhẹ nhàng, không in stacktrace khó chịu
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String id = ctx.channel().id().asShortText();
        if (cause instanceof SocketException
                || cause instanceof ClosedChannelException
                || cause instanceof IOException) {
            log.info("client {} disconnected: {}", id, cause.getMessage());
        } else {
            log.warn("unhandled exception for {}", id, cause);
        }
        sessions.detach(ctx.channel());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String id = ctx.channel().id().asShortText();
        sessions.detach(ctx.channel());
        log.info("channel inactive {}", id);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        sessions.detach(ctx.channel());
        log.info("channel removed {}", ctx.channel().id().asShortText());
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent e
            && e.state() == io.netty.handler.timeout.IdleState.READER_IDLE) {
            log.info("idle timeout, closing {}", ctx.channel().id().asShortText());
            sessions.detach(ctx.channel());
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }
}
