package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import rt.client.model.MapModel;
import rt.client.model.WorldModel;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

public class NetClient {
    private static final ObjectMapper OM = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(NetClient.class);

    private final String url;
    private final WorldModel model;
    private WebSocket ws;

    private final AtomicInteger seq = new AtomicInteger();
    private volatile int lastAck = 0;

    private LongConsumer onClientPong; // HUD ping
    private ScheduledExecutorService pinger;
    public void setOnClientPong(LongConsumer cb) { this.onClientPong = cb; }

    private final ScheduledExecutorService pingSes =
        Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"net-ping"); t.setDaemon(true); return t; });

    // === CHUNK FIELDS ===
    private final rt.client.world.ChunkCache chunkCache = new rt.client.world.ChunkCache();
    private long seed; private int chunkSize = 64; private int tileSize = 32;
    private int lastCenterCx = Integer.MIN_VALUE, lastCenterCy = Integer.MIN_VALUE;

    // expose cho UI/renderer
    public rt.client.world.ChunkCache chunkCache(){ return chunkCache; }
    private java.util.function.IntConsumer onTileSizeChanged;
    public void setOnTileSizeChanged(java.util.function.IntConsumer c){ this.onTileSizeChanged = c; }
    public int tileSize(){ return tileSize; }
    
    private volatile long worldSeed;
    public long getWorldSeed(){ return worldSeed; }
    
    public NetClient setWorldSeed(long worldSeed) {
		this.worldSeed = worldSeed;
		return this;
	}

	private java.util.function.LongConsumer onSeedChanged = s -> {};
    public void setOnSeedChanged(java.util.function.LongConsumer cb){ this.onSeedChanged = (cb!=null?cb:s->{}); }


    public NetClient(String url, WorldModel model) {
        this.url = url;
        this.model = model;
    }

    public void connect(String playerName) {
        OkHttpClient http = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // WS giữ lâu
                .build();

        Request req = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                try {
                    ws.send(OM.writeValueAsString(Map.of("type","hello","name",playerName)));
                    // client-ping mỗi 1s để HUD ping
                    pingSes.scheduleAtFixedRate(() -> {
                        try {
                            long ns = System.nanoTime(); // nanoTime để đo RTT
                            ws.send(OM.writeValueAsString(Map.of("type","cping","ns", ns)));
                        } catch (Exception ignore) {}
                    }, 1000, 1000, TimeUnit.MILLISECONDS);
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode node = Jsons.OM.readTree(text);
                    String type = node.hasNonNull("type") ? node.get("type").asText() : "";
                    if (type == null) { log.warn("missing type"); return; }

                    switch (type) {
                        case "hello" -> {
                            HelloS2C m = Jsons.OM.treeToValue(node, HelloS2C.class);
                            model.setYou(m.you());
                            // Hiện chấm ngay khi chưa kịp state
                            model.spawnAt(3,3);
                        }
                        case "ack" -> {
                            AckS2C m = Jsons.OM.treeToValue(node, AckS2C.class);
                            lastAck = m.seq();
                            model.onAck(m.seq());
                        }
                        case "state" -> {
                            StateS2C st = Jsons.OM.treeToValue(node, StateS2C.class);
                            model.applyStateDTO(st);

                            // ⭐ Reconcile vị trí "you" theo server để collision thể hiện đúng
                            var you = model.you();
                            if (you != null && st.ents() != null) {
                                var me = st.ents().get(you);
                                if (me != null) {
                                    model.reconcileFromServer(me.x(), me.y(), model.lastAck());
                                }
                            }

                            // xin chunk ở vùng mới nếu đã qua ranh giới
                            maybeRequestAround();
                        }
                        case "dev_stats" -> {
                            DevStatsS2C ds = Jsons.OM.treeToValue(node, DevStatsS2C.class);
                            model.applyDevStats(ds.droppedInputs(), ds.streamerSkips(), ds.writable(), ds.ents());
                        }
                        case "error" -> {
                            ErrorS2C er = Jsons.OM.treeToValue(node, ErrorS2C.class);
                            log.warn("server error {}: {}", er.code(), er.message());
                        }
                        case "cpong" -> {
                            long rttMs = -1;
                            if (node.has("ns")) {
                                long ns = node.path("ns").asLong();
                                rttMs = Math.max(0, (System.nanoTime() - ns) / 1_000_000L);
                            } else if (node.has("ts")) {
                                long ts = node.path("ts").asLong();
                                rttMs = Math.max(0, System.currentTimeMillis() - ts);
                            }
                            if (rttMs >= 0 && rttMs <= 5000) model.setPingMs(rttMs);
                            if (onClientPong != null && rttMs >= 0) onClientPong.accept(System.nanoTime());
                        }
                        case "ping" -> {
                            ws.send(Jsons.OM.writeValueAsString(
                                java.util.Map.of("type", "pong", "ts", System.currentTimeMillis())
                            ));
                        } case "seed" -> {
                            seed = node.get("seed").asLong();
                            chunkSize = node.get("chunkSize").asInt();
                            tileSize  = node.get("tileSize").asInt();

                            setWorldSeed(seed);               // <<< QUAN TRỌNG
                            onSeedChanged.accept(worldSeed);       // <<< QUAN TRỌNG

                            model.setMap(null);
                            if (onTileSizeChanged!=null) onTileSizeChanged.accept(tileSize);

                            // xin 3×3 quanh (0,0) lần đầu
                            for (int dy=-rt.client.world.ChunkCache.R; dy<=rt.client.world.ChunkCache.R; dy++)
                                for (int dx=-rt.client.world.ChunkCache.R; dx<=rt.client.world.ChunkCache.R; dx++) {
                                    if (chunkCache.markRequested(dx,dy)) {
                                        ws.send(Jsons.OM.writeValueAsString(new rt.common.net.dto.ChunkReqC2S(dx,dy)));
                                    }
                                }
                        }case "chunk" -> {
                            int cx = node.get("cx").asInt(), cy = node.get("cy").asInt(), size = node.get("size").asInt();
                            byte[] l1 = node.get("layer1").binaryValue(), l2 = node.get("layer2").binaryValue();
                            java.util.BitSet coll = java.util.BitSet.valueOf(node.get("collisionBits").binaryValue());

                            var data = new rt.client.world.ChunkCache.Data(cx,cy,size,l1,l2,coll);
                            chunkCache.onArrive(data);               // giữ 1 lần
                            chunkCache.bakeImage(data, tileSize);
                        }

                        default -> {
                            log.debug("unknown type: {}", type);
                        }
                    }

                } catch (Exception e) { log.error("onMessage error", e); }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) { /* no-op */ }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("connect failed: {}", String.valueOf(t));
                if (response != null) log.error("handshake response: code={} message={}", response.code(), response.message());
            }
        });
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right) {
        try {
            int s = seq.incrementAndGet();
            // báo cho model biết input đã gửi (đúng chữ ký bạn đang dùng)
            model.onInputSent(s, up, down, left, right, System.currentTimeMillis());
            var msg = new InputC2S("input", s, new Keys(up,down,left,right));
            ws.send(Jsons.OM.writeValueAsString(msg));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendClientPing(long ns) {
        try { ws.send(Jsons.OM.writeValueAsString(new ClientPingC2S(ns))); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public void sendAdmin(String token, String cmd) {
        try {
            ws.send(OM.writeValueAsString(java.util.Map.of(
                "type","admin","token", token, "cmd", cmd
            )));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void maybeRequestAround() throws Exception {
        int px = (int)Math.round(model.youX() * tileSize);
        int py = (int)Math.round(model.youY() * tileSize);
        int Npx = chunkSize * tileSize;
        int cx = Math.floorDiv(px, Npx), cy = Math.floorDiv(py, Npx);
        if (cx == lastCenterCx && cy == lastCenterCy) return;
        lastCenterCx = cx; lastCenterCy = cy;

        for (int dy=-rt.client.world.ChunkCache.R; dy<=rt.client.world.ChunkCache.R; dy++)
            for (int dx=-rt.client.world.ChunkCache.R; dx<=rt.client.world.ChunkCache.R; dx++){
                int qx = cx+dx, qy = cy+dy;
                if (!chunkCache.has(qx,qy) && chunkCache.markRequested(qx,qy)) {
                    ws.send(rt.common.net.Jsons.OM.writeValueAsString(new rt.common.net.dto.ChunkReqC2S(qx,qy)));
                }
            }
    }
}
