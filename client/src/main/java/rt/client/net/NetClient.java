package rt.client.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.client.model.WorldModel;
import rt.client.net.geo.GeoThrottle;
import rt.client.net.stream.ChunkStreamController;
import rt.client.world.ChunkCache;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Handles websocket traffic and coordinates chunk/geo streaming. */
public class NetClient {
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(NetClient.class);
    private static final long GEO_MIN_INTERVAL_NS = 150_000_000L; // 150ms

    private final String url;
    private final WorldModel model;
    private WebSocket ws;

    private final AtomicInteger seq = new AtomicInteger();
    private volatile int lastAck = 0;

    private LongConsumer onClientPong;
    public void setOnClientPong(LongConsumer cb) { this.onClientPong = cb; }

    private final ScheduledExecutorService pingSes =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "net-ping");
                t.setDaemon(true);
                return t;
            });

    private final ChunkStreamController chunkStream = new ChunkStreamController();
    private java.util.function.IntConsumer onTileSizeChanged;
    private LongConsumer onSeedChanged = seed -> {};
    private Consumer<GeoS2C> onGeoInfo = gi -> {};
    private final GeoThrottle geoThrottle = new GeoThrottle(GEO_MIN_INTERVAL_NS);

    private volatile long lastInputNs = 0L;
    private volatile int lastMask = -1;

    public NetClient(String url, WorldModel model) {
        this.url = url;
        this.model = model;
    }

    public ChunkCache chunkCache() { return chunkStream.cache(); }
    public int tileSize() { return chunkStream.tileSize(); }
    public boolean hasSeed() { return chunkStream.isReady(); }

    public void setOnTileSizeChanged(java.util.function.IntConsumer c) {
        this.onTileSizeChanged = c;
    }

    public void setOnSeedChanged(LongConsumer cb) {
        this.onSeedChanged = cb != null ? cb : seed -> {};
    }

    public void setOnGeoInfo(Consumer<GeoS2C> cb) {
        this.onGeoInfo = cb != null ? cb : gi -> {};
    }

    public void sendGeoReq(long gx, long gy) {
        if (!geoThrottle.tryAcquire(gx, gy)) {
            return;
        }
        doSendGeo(gx, gy);
    }

    private void doSendGeo(long gx, long gy) {
        try {
            ws.send(Jsons.OM.writeValueAsString(new GeoReqC2S(gx, gy)));
        } catch (Exception e) {
            geoThrottle.cancelInFlight();
            log.warn("failed to send geo_req", e);
        }
    }

    public void tickStreamSafe() {
        try {
            chunkStream.maybeRequestAround(model);
            long[] queued = geoThrottle.tryConsumeQueued();
            if (queued != null) {
                doSendGeo(queued[0], queued[1]);
            }
        } catch (Throwable ignore) {
            // keep scheduler resilient
        }
    }

    public void connect(String playerName) {
        OkHttpClient http = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request req = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    ws.send(OM.writeValueAsString(Map.of("type", "hello", "name", playerName)));
                    pingSes.scheduleAtFixedRate(() -> {
                        try {
                            long ns = System.nanoTime();
                            ws.send(OM.writeValueAsString(Map.of("type", "cping", "ns", ns)));
                        } catch (Exception ignore) {}
                    }, 1000, 1000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.error("Failed to open websocket", e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode node = Jsons.OM.readTree(text);
                    String type = node.hasNonNull("type") ? node.get("type").asText() : "";
                    if (type == null || type.isEmpty()) {
                        log.warn("missing type");
                        return;
                    }

                    switch (type) {
                        case "hello" -> handleHello(node);
                        case "ack" -> handleAck(node);
                        case "state" -> handleState(node);
                        case "dev_stats" -> handleDevStats(node);
                        case "error" -> handleError(node);
                        case "cpong" -> handleClientPong(node);
                        case "ping" -> sendPong();
                        case "seed" -> handleSeed(node);
                        case "geo" -> handleGeo(node);
                        default -> log.debug("unknown type: {}", type);
                    }
                } catch (Exception e) {
                    log.error("onMessage error", e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                // no-op: binary frames unused
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("connect failed: {}", t.toString());
                if (response != null) {
                    log.error("handshake response: code={} message={}", response.code(), response.message());
                }
            }
        });
    }

    private void handleHello(JsonNode node) throws Exception {
        HelloS2C m = Jsons.OM.treeToValue(node, HelloS2C.class);
        model.setYou(m.you());
        model.spawnAt(3, 3);
    }

    private void handleAck(JsonNode node) throws Exception {
        AckS2C m = Jsons.OM.treeToValue(node, AckS2C.class);
        lastAck = m.seq();
        model.onAck(m.seq());
    }

    private void handleState(JsonNode node) throws Exception {
        StateS2C st = Jsons.OM.treeToValue(node, StateS2C.class);
        model.applyStateDTO(st);

        var you = model.you();
        if (you != null && st.ents() != null) {
            var me = st.ents().get(you);
            if (me != null) {
                model.reconcileFromServer(me.x(), me.y(), model.lastAck());
            }
        }

        chunkStream.maybeRequestAround(model);
    }

    private void handleDevStats(JsonNode node) throws Exception {
        DevStatsS2C ds = Jsons.OM.treeToValue(node, DevStatsS2C.class);
        model.applyDevStats(ds.droppedInputs(), ds.streamerSkips(), ds.writable(), ds.ents());
    }

    private void handleError(JsonNode node) throws Exception {
        ErrorS2C er = Jsons.OM.treeToValue(node, ErrorS2C.class);
        log.warn("server error {}: {}", er.code(), er.message());
    }

    private void handleClientPong(JsonNode node) {
        long rttMs = -1;
        if (node.has("ns")) {
            long ns = node.path("ns").asLong();
            rttMs = Math.max(0, (System.nanoTime() - ns) / 1_000_000L);
        } else if (node.has("ts")) {
            long ts = node.path("ts").asLong();
            rttMs = Math.max(0, System.currentTimeMillis() - ts);
        }
        if (rttMs >= 0 && rttMs <= 5000) {
            model.setPingMs(rttMs);
        }
        if (onClientPong != null && rttMs >= 0) {
            onClientPong.accept(System.nanoTime());
        }
    }

    private void sendPong() {
        try {
            ws.send(Jsons.OM.writeValueAsString(
                    Map.of("type", "pong", "ts", System.currentTimeMillis())));
        } catch (Exception e) {
            log.warn("failed to send pong", e);
        }
    }

    private void handleSeed(JsonNode node) throws Exception {
        long seed = node.get("seed").asLong();
        int chunkSize = node.get("chunkSize").asInt();
        int tileSize = node.get("tileSize").asInt();

        rt.common.world.WorldGenerator.configure(new rt.common.world.WorldGenConfig(seed, 0.55, 0.35));
        chunkStream.applySeed(model, seed, chunkSize, tileSize);

        if (onTileSizeChanged != null) {
            onTileSizeChanged.accept(tileSize);
        }
        onSeedChanged.accept(seed);
    }

    private void handleGeo(JsonNode node) throws Exception {
        GeoS2C gi = Jsons.OM.treeToValue(node, GeoS2C.class);
        long[] queued = geoThrottle.releaseAndNext();
        try {
            onGeoInfo.accept(gi);
        } catch (Exception ignore) {
        }
        if (queued != null) {
            doSendGeo(queued[0], queued[1]);
        }
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right) {
        int mask = (up ? 1 : 0) | (down ? 2 : 0) | (left ? 4 : 0) | (right ? 8 : 0);
        if (mask == lastMask) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastInputNs < 33_000_000L) {
            return;
        }

        lastMask = mask;
        lastInputNs = now;
        doSendInput(up, down, left, right);
    }

    public void doSendInput(boolean up, boolean down, boolean left, boolean right) {
        try {
            int s = seq.incrementAndGet();
            model.onInputSent(s, up, down, left, right, System.currentTimeMillis());
            var msg = new InputC2S("input", s, new Keys(up, down, left, right));
            ws.send(Jsons.OM.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("failed to send input", e);
        }
    }

    public void sendClientPing(long ns) {
        try {
            ws.send(Jsons.OM.writeValueAsString(new ClientPingC2S(ns)));
        } catch (Exception e) {
            log.warn("failed to send client ping", e);
        }
    }

    public void sendAdmin(String token, String cmd) {
        try {
            ws.send(OM.writeValueAsString(Map.of(
                    "type", "admin", "token", token, "cmd", cmd
            )));
        } catch (Exception e) {
            log.warn("failed to send admin command", e);
        }
    }
}
