package rt.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.common.net.ErrorCodes;
import rt.common.net.Jsons;
import rt.common.net.dto.*;
import rt.common.world.WorldGenConfig;
import rt.server.config.ServerConfig;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.session.SessionRegistry.Session;
import rt.server.websocket.handler.AdminCommandHandler;
import rt.server.websocket.handler.GeoRequestLimiter;
import rt.server.websocket.handler.InputRateLimiter;
import rt.server.world.World;
import rt.server.world.chunk.ChunkService;
import rt.server.world.geo.ContinentIndex;
import rt.server.world.geo.GeoService;
import rt.server.world.geo.SeaIndex;

import java.util.Map;
import java.util.Objects;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(WsTextHandler.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private static final int INPUT_MAX_PER_SEC = 60;
    private static final long INPUT_WINDOW_MS = 1000L;
    private static final int GEO_MAX_PER_SEC = 5;
    private static final long GEO_WINDOW_MS = 1000L;

    private final SessionRegistry sessions;
    private final InputQueue inputs;
    private final World world;
    private final ServerConfig cfg;
    private final ChunkService chunkService;
    private final ContinentIndex continents;
    private final SeaIndex seas;
    private final GeoService geo;

    private final InputRateLimiter inputLimiter;
    private final GeoRequestLimiter geoLimiter;
    private final AdminCommandHandler adminCommands;

    public WsTextHandler(SessionRegistry sessions, InputQueue inputs, World world, ServerConfig cfg,
                         ChunkService chunkService, ContinentIndex continents,
                         SeaIndex seas, WorldGenConfig cfgGen) {
        this.sessions = sessions;
        this.inputs = inputs;
        this.world = world;
        this.cfg = cfg;
        this.chunkService = chunkService;
        this.continents = continents;
        this.seas = seas;
        this.geo = new GeoService(new rt.common.world.WorldGenerator(cfgGen), continents, seas);
        this.inputLimiter = new InputRateLimiter(INPUT_MAX_PER_SEC, INPUT_WINDOW_MS, 1000L);
        this.geoLimiter = new GeoRequestLimiter(GEO_MAX_PER_SEC, GEO_WINDOW_MS, 1000L);
        this.adminCommands = new AdminCommandHandler(sessions, world, continents, cfg);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Session s = new Session(ctx.channel(), ctx.channel().id().asShortText());
        sessions.attach(s);
        log.info("channel added {}", s.playerId);
        log.info("Use ChunkService {}", System.identityHashCode(this.chunkService));
        s.send(Map.of("type", "hello", "you", s.playerId));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        JsonNode node = Jsons.OM.readTree(frame.text());
        String type = node.path("type").asText(null);
        if (type == null) {
            log.warn("missing type");
            return;
        }

        Session s = sessions.byChannel(ctx.channel());
        if (s == null) {
            log.warn("no session");
            return;
        }

        switch (type) {
            case "hello" -> handleHello(s);
            case "input" -> handleInput(node, s);
            case "admin" -> handleAdmin(node, s);
            case "ping" -> handleServerPing(node, s);
            case "cping" -> handleClientPing(node, s);
            case "cpong" -> { /* ignore */ }
            case "geo_req" -> handleGeoRequest(node, s);
            default -> log.warn("unknown type {}", type);
        }
    }

    private void handleHello(Session session) {
        session.send(new HelloS2C(session.playerId));
        session.send(new SeedS2C(ServerConfig.worldSeed, rt.common.world.ChunkPos.SIZE, 32));
    }

    private void handleInput(JsonNode node, Session session) throws Exception {
        var in = Jsons.OM.treeToValue(node, InputC2S.class);
        if (!inputLimiter.allow(session.playerId, session)) {
            return;
        }
        var keys = in.keys();
        inputs.offer(session.playerId, in.seq(),
                Map.of("up", keys.up(), "down", keys.down(), "left", keys.left(), "right", keys.right()));
        session.send(new AckS2C(in.seq()));
    }

    private void handleAdmin(JsonNode node, Session session) throws Exception {
        var ad = Jsons.OM.treeToValue(node, AdminC2S.class);
        if (!Objects.equals(ad.token(), cfg.adminToken)) {
            session.send(new ErrorS2C(ErrorCodes.ADMIN_UNAUTHORIZED, "Bad or missing admin token"));
            return;
        }
        session.send(adminCommands.handle(session, ad.cmd()));
    }

    private void handleServerPing(JsonNode node, Session session) throws Exception {
        PongC2S pg = Jsons.OM.treeToValue(node, PongC2S.class);
        long rtt = System.currentTimeMillis() - pg.ts();
        log.debug("server RTT {} = {} ms", session.playerId, rtt);
    }

    private void handleClientPing(JsonNode node, Session session) throws Exception {
        Map<String, Object> root = OM.readValue(node.toString(), new TypeReference<Map<String, Object>>(){});
        Object ns = root.get("ns");
        if (ns instanceof Number n) {
            session.send(Map.of("type", "cpong", "ns", n.longValue()));
        } else {
            long ts = ((Number) root.getOrDefault("ts", System.currentTimeMillis())).longValue();
            session.send(Map.of("type", "cpong", "ts", ts));
        }
    }

    private void handleGeoRequest(JsonNode node, Session session) throws Exception {
        GeoReqC2S req = Jsons.OM.treeToValue(node, GeoReqC2S.class);
        if (!geoLimiter.allow(session.playerId, session)) {
            return;
        }
        GeoService.GeoInfo info = geo.at(req.gx(), req.gy());
        session.send(new GeoS2C(req.gx(), req.gy(),
                info.terrainId, info.terrainName,
                info.continentId, info.continentName,
                info.seaId, info.seaName));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String msg = String.valueOf(cause.getMessage());
        String low = msg == null ? "" : msg.toLowerCase();
        boolean benign = cause instanceof java.net.SocketException
                || cause instanceof java.nio.channels.ClosedChannelException
                || (cause instanceof java.io.IOException
                && (low.contains("connection reset") || low.contains("by peer")
                || low.contains("forcibly closed") || low.contains("broken pipe")));
        if (benign) {
            if (log.isDebugEnabled()) {
                log.debug("client disconnected: {}", msg);
            }
            ctx.close();
            return;
        }
        if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            ctx.close();
            return;
        }
        if (cause instanceof io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException) {
            ctx.close();
            return;
        }
        log.warn("WS pipeline exception", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Session s = sessions.byChannel(ctx.channel());
        if (s != null) {
            world.removePlayer(s.playerId);
            inputs.remove(s.playerId);
        }
        sessions.detach(ctx.channel());
        log.info("channel inactive {}", ctx.channel().id().asShortText());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Session s = sessions.byChannel(ctx.channel());
        if (s != null) {
            world.removePlayer(s.playerId);
            inputs.remove(s.playerId);
        }
        sessions.detach(ctx.channel());
        log.info("channel removed {}", ctx.channel().id().asShortText());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) {
            log.info("idle timeout {}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Session s = sessions.attach(ctx.channel());
        s.send(new HelloS2C(s.playerId));
        long seed = (cfg.worldSeed != 0 ? cfg.worldSeed : 20250917L);
        s.send(new SeedS2C(seed, rt.common.world.ChunkPos.SIZE, 32));
    }
}
