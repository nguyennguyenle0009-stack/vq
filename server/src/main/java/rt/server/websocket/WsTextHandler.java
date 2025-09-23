package rt.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import io.netty.channel.Channel;
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
import rt.server.world.geo.ContinentIndex;
import rt.server.world.geo.GeoService;
import rt.server.world.geo.SeaIndex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import rt.common.net.ErrorCodes;
import rt.common.net.Jsons;
import rt.common.net.dto.*;
import rt.common.world.WorldGenConfig;

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
    private ChunkService chunkservice;
    private final ContinentIndex continents;
    private final SeaIndex seas;            // ★ NEW
    private final GeoService geo;           // ★ keep field
    private final WorldGenConfig cfgGen;

    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private static final ExecutorService WORLD_EXEC = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "ws-world-" + WORKER_ID.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
    );


    // ====== NEW: Overworld (chunk) foundation ======
    private static final int  TILE_SIZE  = 32;
    
 // Geo RL
    private static final int  GEO_MAX_PER_SEC = 5;
    private static final long GEO_WINDOW_MS   = 1000L;
    private static final class GeoRL { long winStart; int count; }
    private final java.util.concurrent.ConcurrentHashMap<String, GeoRL> geoRl = new java.util.concurrent.ConcurrentHashMap<>();


    
    public WsTextHandler(SessionRegistry sessions, InputQueue inputs, World world, ServerConfig cfg,
            ChunkService chunkservice, ContinentIndex continents,
            rt.server.world.geo.SeaIndex seas, rt.common.world.WorldGenConfig cfgGen) { // ★ NEW
			this.sessions=sessions; this.inputs=inputs; this.world=world; this.cfg=cfg;
			this.chunkservice=chunkservice; this.continents=continents;
			this.seas = seas; this.cfgGen = cfgGen;                                                      // ★ NEW
			
			// DÙNG CHUNG CẤU HÌNH VỚI CHUNK
			this.geo = new rt.server.world.geo.GeoService(
			new rt.common.world.WorldGenerator(cfgGen), continents, seas
			);
	}

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.info("channel added {}", ctx.channel().id().asShortText());
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
                s.send(new SeedS2C(cfgGen.seed, rt.common.world.ChunkPos.SIZE, TILE_SIZE));
            }

            // === PHASE 1: client xin chunk (cx,cy) ===
            case "chunk_req" -> {
                int cx = node.get("cx").asInt();
                int cy = node.get("cy").asInt();
                submitChunkTask(ctx.channel(), cx, cy);
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
                    } else if (cmd.equals("cont here")) {
                        long tx = Math.round(s.x), ty = Math.round(s.y);
                        int cid = continents.idAtTile(tx, ty);
                        var m = continents.meta(cid);
                        s.send(new AdminResultS2C(true,
                            "contId=" + cid + (m!=null? (" name="+m.name+" areaCells="+m.areaCells) : "")));
                    } else if (cmd.equals("cont list")) {
                        StringBuilder sb = new StringBuilder();
                        for (var m : continents.all())
                            sb.append(m.id).append(' ').append(m.name).append(" area=").append(m.areaCells).append('\n');
                        s.send(new AdminResultS2C(true, sb.length()==0? "(empty)" : sb.toString()));
                    } else if (cmd.startsWith("cont goto ")) {
                        int cid = Integer.parseInt(cmd.substring(9).trim());
                        var m = continents.meta(cid);
                        if (m == null) { s.send(new AdminResultS2C(false, "unknown continent id")); break; }
                        int C = continents.cellSizeTiles();
                        double tx = m.ax * (double)C + C * 0.5, ty = m.ay * (double)C + C * 0.5;
                        boolean ok = world.teleport(s.playerId, tx, ty);
                        s.send(new AdminResultS2C(ok, ok? ("teleported to "+m.name+" (#"+cid+")") : "teleport failed"));
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
            }case "geo_req" -> {
                long gx = node.path("gx").asLong();
                long gy = node.path("gy").asLong();

                // rate-limit per session
                GeoRL r = geoRl.computeIfAbsent(s.playerId, k -> new GeoRL());
                long now = System.currentTimeMillis();
                if (now - r.winStart >= GEO_WINDOW_MS) { r.winStart = now; r.count = 0; }
                if (++r.count > GEO_MAX_PER_SEC) {
                    // lặng lẽ bỏ qua để không nghẽn (không gửi error/pending)
                    return;
                }

                submitGeoTask(ctx.channel(), gx, gy);
            }


            default -> log.warn("unknown type {}", type);
        }
    }

    private void submitChunkTask(Channel channel, int cx, int cy) {
        WORLD_EXEC.execute(() -> {
            try {
                Session session = sessions.byChannel(channel);
                if (session == null || !channel.isActive()) {
                    return;
                }
                var cd = chunkservice.get(cx, cy);
                session.send(new ChunkS2C(cd.cx, cd.cy, cd.size, cd.layer1, cd.layer2, cd.collision.toByteArray()));
            } catch (Exception ex) {
                log.warn("chunk_req failed cx={} cy={}", cx, cy, ex);
            }
        });
    }

    private void submitGeoTask(Channel channel, long gx, long gy) {
        WORLD_EXEC.execute(() -> {
            try {
                Session session = sessions.byChannel(channel);
                if (session == null || !channel.isActive()) {
                    return;
                }
                var info = geo.at(gx, gy);
                session.send(new GeoS2C("geo", gx, gy,
                        info.terrainId, info.terrainName,
                        info.continentId, info.continentName,
                        info.seaId, info.seaName));
            } catch (Exception ex) {
                log.warn("geo_req failed gx={} gy={}", gx, gy, ex);
            }
        });
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
        if (s != null) {
            world.removePlayer(s.playerId);
            inputs.remove(s.playerId);
            rl.remove(s.playerId);
            geoRl.remove(s.playerId);
            lastPong.remove(s.playerId);
        }
        sessions.detach(ctx.channel());
        log.info("channel inactive {}", id);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Session s = sessions.byChannel(ctx.channel());
        if (s != null) {
            world.removePlayer(s.playerId);
            inputs.remove(s.playerId);
            rl.remove(s.playerId);
            geoRl.remove(s.playerId);
            lastPong.remove(s.playerId);
        }
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

        // Đăng ký session cho channel mới; phản hồi hello/seed sẽ gửi khi client gửi "hello"
        sessions.attach(ctx.channel());
    }
}
