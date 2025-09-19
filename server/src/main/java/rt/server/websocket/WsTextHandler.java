package rt.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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
import rt.server.world.chunk.ChunkService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rt.common.net.ErrorCodes;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WsTextHandler.class);
    private static final ObjectMapper OM = new ObjectMapper();

    // heartbeat/debug
    private final ConcurrentHashMap<String, Long> lastPong = new ConcurrentHashMap<>();

    // Rate-limit input
    private static final int  INPUT_MAX_PER_SEC = 60;
    private static final long INPUT_WINDOW_MS   = 1000L;
    private static final class RL { long winStart; int count; long lastNotify; }
    private final ConcurrentHashMap<String, RL> rl = new ConcurrentHashMap<>();

    private final SessionRegistry sessions;
    private final InputQueue inputs;
    private final World world;
    private final ServerConfig cfg;
    private final ChunkService chunkservice;


    // ====== NEW: Overworld (chunk) foundation ======
    private static final int  TILE_SIZE  = 32;
    
    public WsTextHandler(
    		SessionRegistry sessions, 
    		InputQueue inputs, 
    		World world, 
    		ServerConfig cfg, 
                ChunkService chunkservice) {
        this.sessions = sessions;
        this.inputs   = inputs;
        this.world    = world;
        this.cfg      = cfg;
        this.chunkservice = chunkservice;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        String id = ctx.channel().id().asShortText();
        Session s = new Session(ctx.channel(), id);
        sessions.attach(s);
        log.info("channel added {}", id);
        log.info("Use ChunkService {}", System.identityHashCode(this.chunkservice));
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

                // === PHASE 1: bật chế độ chunk ở client bằng seed ===
                var genCfg = this.chunkservice.config();
                s.send(new SeedS2C(genCfg.seed, rt.common.world.ChunkPos.SIZE, TILE_SIZE, genCfg.plainRatio, genCfg.forestRatio));
            }

            // === PHASE 1: client xin chunk (cx,cy) ===
            case "chunk_req" -> {
                int cx = node.get("cx").asInt();
                int cy = node.get("cy").asInt();
                var cd = this.chunkservice.get(cx, cy);
                s.send(new ChunkS2C(cd.cx, cd.cy, cd.size, cd.layer1, cd.layer2, cd.collision.toByteArray()));
            }

            case "input" -> {
                var in = Jsons.OM.treeToValue(node, InputC2S.class);
                if (!allowInputAndMaybeWarn(s.playerId, s)) return;
                var k = in.keys();
                inputs.offer(s.playerId, in.seq(),
                        Map.of("up",k.up(),"down",k.down(),"left",k.left(),"right",k.right()));
                s.send(new AckS2C(in.seq()));
            }

            case "admin" -> {
                var ad = Jsons.OM.treeToValue(node, AdminC2S.class);
                if (ad.token() == null || !ad.token().equals(cfg.adminToken)) {
                    s.send(new ErrorS2C(ErrorCodes.ADMIN_UNAUTHORIZED,"Bad or missing admin token"));
                    break;
                }
                String cmd = ad.cmd() == null ? "" : ad.cmd().trim();
                try {
                    if (cmd.equals("listSessions")) {
                        StringBuilder sb = new StringBuilder();
                        sessions.all().forEach(x -> sb.append(x.playerId).append(' '));
                        s.send(new AdminResultS2C(true, "sessions: " + sb.toString().trim()));
                    } else if (cmd.startsWith("teleport ")) {
                        String[] p = cmd.split("\\s+");
                        if (p.length != 4) { s.send(new AdminResultS2C(false,"usage: teleport <id> <x> <y>")); break; }
                        boolean ok = world.teleport(p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]));
                        s.send(new AdminResultS2C(ok, ok ? "teleported" : "failed (blocked/out-of-bounds)"));
                    } else if (cmd.equals("reloadMap")) {
                        boolean ok = world.reloadMap(cfg.mapResourcePath);
                        s.send(new AdminResultS2C(ok, ok ? "map reloaded" : "reload failed"));
                    } else {
                        s.send(new AdminResultS2C(false, "unknown cmd"));
                    }
                } catch (Exception ex) {
                    s.send(new AdminResultS2C(false, "error: " + ex.getMessage()));
                }
            }

            case "ping" -> {
                PongC2S pg = Jsons.OM.treeToValue(node, PongC2S.class);
                long rtt = System.currentTimeMillis() - pg.ts();
                log.debug("server RTT {} = {} ms", s.playerId, rtt);
            }

            case "cpong" -> { /* ignore */ }
            case "cping" -> {
                Map<String,Object> root = OM.readValue(text,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                Object ns = root.get("ns");
                if (ns instanceof Number n) {
                    s.send(Map.of("type", "cpong", "ns", n.longValue()));
                } else {
                    long ts = ((Number) root.getOrDefault("ts", System.currentTimeMillis())).longValue();
                    s.send(Map.of("type", "cpong", "ts", ts));
                }
            }

            default -> log.warn("unknown type {}", type);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String msg = String.valueOf(cause.getMessage()); String low = msg==null? "": msg.toLowerCase();
        boolean benign = cause instanceof java.net.SocketException
                || cause instanceof java.nio.channels.ClosedChannelException
                || (cause instanceof java.io.IOException
                && (low.contains("connection reset") || low.contains("by peer")
                || low.contains("forcibly closed") || low.contains("broken pipe")));
        if (benign) { if (log.isDebugEnabled()) log.debug("client disconnected: {}", msg); ctx.close(); return; }
        if (cause instanceof io.netty.handler.codec.TooLongFrameException) { ctx.close(); return; }
        if (cause instanceof io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException) { ctx.close(); return; }
        log.warn("WS pipeline exception", cause); ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String id = ctx.channel().id().asShortText();
        Session s = sessions.byChannel(ctx.channel());
        if (s != null) { world.removePlayer(s.playerId); inputs.remove(s.playerId); }
        sessions.detach(ctx.channel());
        log.info("channel inactive {}", id);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Session s = sessions.byChannel(ctx.channel());
        if (s != null) { world.removePlayer(s.playerId); inputs.remove(s.playerId); }
        sessions.detach(ctx.channel());
        log.info("channel removed {}", ctx.channel().id().asShortText());
    }

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

    private boolean allowInputAndMaybeWarn(String playerId, Session s) {
        long now = System.currentTimeMillis();
        RL r = rl.computeIfAbsent(playerId, k -> new RL());
        if (now - r.winStart >= INPUT_WINDOW_MS) { r.winStart = now; r.count = 0; }
        r.count++;
        if (r.count <= INPUT_MAX_PER_SEC) return true;
        if (now - r.lastNotify >= 1000L) {
            r.lastNotify = now;
            s.droppedInputs.incrementAndGet();
            s.send(new ErrorS2C(ErrorCodes.RATE_LIMIT_INPUT,
                    "Too many inputs (> " + INPUT_MAX_PER_SEC + "/s). Some inputs are dropped."));
        }
        return false;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        // Tạo & đăng ký session từ channel hiện tại
        var s = sessions.attach(ctx.channel());
        s.send(new HelloS2C(s.playerId));

        // Gửi seed cho client dựa trên cấu hình generator hiện tại
        var genCfg = this.chunkservice.config();
        s.send(new SeedS2C(genCfg.seed, rt.common.world.ChunkPos.SIZE, 32, genCfg.plainRatio, genCfg.forestRatio)); // tileSize tùy bạn
    }
}