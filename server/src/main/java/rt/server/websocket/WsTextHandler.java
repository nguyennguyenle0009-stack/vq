// WsTextHandler.java
package rt.server.websocket;

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

import com.fasterxml.jackson.databind.JsonNode;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

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
        JsonNode node = Jsons.OM.readTree(text);
        String type = node.path("type").asText(null);
        if (type == null) { log.warn("missing type"); return; }

        Session s = sessions.byChannel(ctx.channel());
        if (s == null) { log.warn("no session"); return; }

        switch (type) {
            case "hello" -> {
                HelloC2S msg = Jsons.OM.treeToValue(node, HelloC2S.class);
                log.info("hello from {} name={}", s.playerId, msg.name());
                s.send(new HelloS2C(s.playerId)); // {"type":"hello","you":...}
            }
            case "input" -> {
                InputC2S in = Jsons.OM.treeToValue(node, InputC2S.class);
                Keys k = in.keys();
                inputs.offer(s.playerId, in.seq(), Map.of(
                        "up", k.up(), "down", k.down(), "left", k.left(), "right", k.right()
                ));
                s.send(new AckS2C(in.seq()));
            }
            case "ping" -> {
                PongC2S pg = Jsons.OM.treeToValue(node, PongC2S.class);
                long rtt = System.currentTimeMillis() - pg.ts();
                // lưu metrics nếu cần; ở đây log nhẹ (DEBUG)
                log.debug("server RTT {} = {} ms", s.playerId, rtt);
            }
//            case "cpong" -> {
//                ClientPongS2C cp = Jsons.OM.treeToValue(node, ClientPongS2C.class);
//                if (onClientPong != null) onClientPong.accept(cp.ns()); // RTT client-side
//            }
            case "cping" -> {
                ClientPingC2S cp = Jsons.OM.treeToValue(node, ClientPingC2S.class);
                s.send(new ClientPongS2C(cp.ns())); // echo ns để client tự đo RTT
            }
            default -> log.warn("unknown type {}", type);
        }
    }
    


    // client đóng → dọn dẹp nhẹ nhàng, không in stacktrace khó chịu
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String cid = ctx.channel().id().asShortText();
        String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();

        // Nhóm lỗi phổ biến khi client đóng đột ngột
        if (cause instanceof java.net.SocketException && "Connection reset".equalsIgnoreCase(msg)) {
            log.info("connection reset {}", cid);
        } else if (cause instanceof io.netty.handler.codec.CorruptedFrameException) {
            log.warn("bad websocket frame {}: {}", cid, msg); // không in full stack
        } else if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            log.warn("frame too large {}: {}", cid, msg);     // bị chặn bởi maxFramePayloadLength
        } else {
            // Chỉ in stacktrace khi DEBUG, còn lại log ngắn gọn
            if (log.isDebugEnabled()) log.debug("ws error " + cid, cause);
            else log.warn("ws error {}: {}", cid, msg);
        }
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
