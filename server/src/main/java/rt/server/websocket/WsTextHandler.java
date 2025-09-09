// WsTextHandler.java
package rt.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rt.server.config.ServerConfig;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.session.SessionRegistry.Session;
import rt.server.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WsTextHandler.class);
    private static final ObjectMapper OM = new ObjectMapper();
    
    // theo dõi heartbeat (không bắt buộc, dùng để debug)
    private final ConcurrentHashMap<String, Long> lastPong = new ConcurrentHashMap<>();
    
 // Rate-limit input: <= 60 msg/s per player, notify at most 1/sec
    private static final int  INPUT_MAX_PER_SEC = 60;
    private static final long INPUT_WINDOW_MS   = 1000L;
    private static final class RL { long winStart; int count; long lastNotify; }
    private final java.util.concurrent.ConcurrentHashMap<String, RL> rl = new java.util.concurrent.ConcurrentHashMap<>();
    
    private final SessionRegistry sessions;
    private final InputQueue inputs;
    private final World world;
    private final ServerConfig cfg;

    public WsTextHandler(SessionRegistry sessions, InputQueue inputs, World world, ServerConfig cfg) {
    	this.sessions = sessions; 
    	this.inputs = inputs; 
    	this.world = world; 
    	this.cfg = cfg;
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
	            s.send(new HelloS2C(s.playerId));
	
	            // Gửi map một lần sau hello
	            var m = world.map(); // thêm field world vào WsTextHandler (xem chú thích bên dưới)
	            s.send(new MapS2C(m.tile, m.w, m.h, m.solidLines()));
	        }
	        case "input" -> {
	            var in = Jsons.OM.treeToValue(node, rt.common.net.dto.InputC2S.class);
	            if (!allowInputAndMaybeWarn(s.playerId, s)) return; // drop + báo lỗi đã gửi
	            var k = in.keys();
	            inputs.offer(s.playerId, in.seq(),
	                java.util.Map.of("up",k.up(),"down",k.down(),"left",k.left(),"right",k.right()));
	            s.send(new rt.common.net.dto.AckS2C(in.seq()));
	        }

	        case "admin" -> {
	            var ad = Jsons.OM.treeToValue(node, rt.common.net.dto.AdminC2S.class);
	            if (ad.token() == null || !ad.token().equals(cfg.adminToken)) {
	                s.send(new rt.common.net.dto.ErrorS2C("ADMIN_UNAUTHORIZED","Bad or missing admin token"));
	                break;
	            }
	            String cmd = ad.cmd() == null ? "" : ad.cmd().trim();
	            try {
	                if (cmd.equals("listSessions")) {
	                    StringBuilder sb = new StringBuilder();
	                    sessions.all().forEach(x -> sb.append(x.playerId).append(' '));
	                    s.send(new rt.common.net.dto.AdminResultS2C(true, "sessions: " + sb.toString().trim()));
	                } else if (cmd.startsWith("teleport ")) {
	                    String[] p = cmd.split("\\s+");
	                    if (p.length != 4) { s.send(new rt.common.net.dto.AdminResultS2C(false,"usage: teleport <id> <x> <y>")); break; }
	                    boolean ok = world.teleport(p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]));
	                    s.send(new rt.common.net.dto.AdminResultS2C(ok, ok ? "teleported" : "failed (blocked/out-of-bounds)"));
	                } else if (cmd.equals("reloadMap")) {
	                    boolean ok = world.reloadMap(cfg.mapResourcePath);
	                    s.send(new rt.common.net.dto.AdminResultS2C(ok, ok ? "map reloaded" : "reload failed"));
	                } else {
	                    s.send(new rt.common.net.dto.AdminResultS2C(false, "unknown cmd"));
	                }
	            } catch (Exception ex) {
	                s.send(new rt.common.net.dto.AdminResultS2C(false, "error: " + ex.getMessage()));
	            }
	        }
            case "ping" -> {
                PongC2S pg = Jsons.OM.treeToValue(node, PongC2S.class);
                long rtt = System.currentTimeMillis() - pg.ts();
                // lưu metrics nếu cần; ở đây log nhẹ (DEBUG)
                log.debug("server RTT {} = {} ms", s.playerId, rtt);
            }
//          case "cpong" -> { }
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
    
    // đóng idle
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.timeout.IdleStateEvent e
             && e.state() == io.netty.handler.timeout.IdleState.READER_IDLE) {
            log.info("idle timeout {}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
    
    private boolean allowInputAndMaybeWarn(String playerId, SessionRegistry.Session s) {
        long now = System.currentTimeMillis();
        RL r = rl.computeIfAbsent(playerId, k -> new RL());
        if (now - r.winStart >= INPUT_WINDOW_MS) { r.winStart = now; r.count = 0; }
        r.count++;
        if (r.count <= INPUT_MAX_PER_SEC) return true;

        // drop + notify (throttle 1s)
        if (now - r.lastNotify >= 1000L) {
            r.lastNotify = now;
            s.send(new rt.common.net.dto.ErrorS2C("RATE_LIMIT_INPUT",
                    "Too many inputs (> " + INPUT_MAX_PER_SEC + "/s). Some inputs are dropped."));
        }
        return false;
    }
}
