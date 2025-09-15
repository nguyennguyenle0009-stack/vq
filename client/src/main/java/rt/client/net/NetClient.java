package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import rt.client.model.MapModel;
import rt.client.model.WorldModel;
import rt.common.map.codec.BitsetRLE;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

 // ==== handshake / chunk state (tối thiểu) ====
    private volatile String worldId = null;
    private volatile int tileSizeHs = 16, chunkSizeHs = 32, viewDist = 2;

    // theo dõi vị trí của chính bạn từ gói state
    private volatile double youX = 0, youY = 0;

    // throttle yêu cầu chunk
    private Integer lastCx = null, lastCy = null;
    private long lastReqAt = 0L;
    private static final long CHUNK_KEEPALIVE_MS = 1000L;

    // cache tạm (chưa render chunk, chỉ lưu để sẵn)
    private final ConcurrentMap<Long, int[]> chunkTiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, BitSet> chunkSolid = new ConcurrentHashMap<>();
    private static long ckey(int cx, int cy){ return (((long)cx)<<32) ^ (cy & 0xffffffffL); }
    
    public NetClient(String url, WorldModel model) {
        this.url = url;
        this.model = model;
    }
    
    private void maybeRequestChunks() {
        if (worldId == null || ws == null) return;

        // quy vị trí tile -> chunk
        int centerCx = (int) Math.floor(youX / (double) chunkSizeHs);
        int centerCy = (int) Math.floor(youY / (double) chunkSizeHs);
        int radius   = Math.max(1, viewDist);

        long now = System.currentTimeMillis();
        boolean crossed = lastCx == null || lastCy == null || centerCx != lastCx || centerCy != lastCy;
        boolean keepAlive = (now - lastReqAt) >= CHUNK_KEEPALIVE_MS;

        if (crossed || keepAlive) {
            try {
                // gửi JSON thuần để không lệ thuộc constructor DTO
                ws.send(OM.writeValueAsString(Map.of(
                    "type", "chunkReq",
                    "worldId", worldId,
                    "centerCx", centerCx,
                    "centerCy", centerCy,
                    "radius", radius
                )));
            } catch (Exception e) { log.warn("send chunkReq failed", e); }
            lastCx = centerCx; lastCy = centerCy; lastReqAt = now;
        }
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
                      case "map" -> {
                          MapS2C m = Jsons.OM.treeToValue(node, MapS2C.class);
                          model.setMap(MapModel.fromLines(m.tile(), m.w(), m.h(), m.solidLines()));
                      }
				      case "ack" -> {
				          AckS2C m = Jsons.OM.treeToValue(node, AckS2C.class);
				          model.onAck(m.seq());
				      }
				      case "state" -> {
				          StateS2C st = Jsons.OM.treeToValue(node, StateS2C.class);
				          model.applyStateDTO(st);
				          var you = model.you();
				          if (you != null && st.ents()!=null) {
				              var es = st.ents().get(you);
				              if (es != null) model.reconcileFromServer(es.x(), es.y(), model.lastAck());
				              // NEW: cập nhật vị trí của bạn và thử gửi chunkReq
				              youX = es.x(); youY = es.y();
				              maybeRequestChunks();
				          }
				      }
				      case "dev_stats" -> {
				          DevStatsS2C ds = Jsons.OM.treeToValue(node, DevStatsS2C.class);
				          model.applyDevStats(ds.droppedInputs(), ds.streamerSkips(), ds.writable(), ds.ents());
				      }
				      case "error" -> {
				          ErrorS2C er = Jsons.OM.treeToValue(node, ErrorS2C.class);
				          log.warn("server error {}: {}", er.code(), er.message());
				      }case "cpong" -> {
			               long rttMs = -1;
			               if (node.has("ns")) {
			                   long ns = node.path("ns").asLong();
			                   rttMs = Math.max(0, (System.nanoTime() - ns) / 1_000_000L);
			               } else if (node.has("ts")) {
			                   long ts = node.path("ts").asLong();
			                   rttMs = Math.max(0, System.currentTimeMillis() - ts);
			               }
			               if (rttMs >= 0 && rttMs <= 5000) model.setPingMs(rttMs);
			           }case "ping" -> {
			                ws.send(Jsons.OM.writeValueAsString(
			                    java.util.Map.of("type", "pong", "ts", System.currentTimeMillis())
			                ));
		               }case "worldHandshake" -> {
		            	    WorldHandshakeS2C hs = Jsons.OM.treeToValue(node, WorldHandshakeS2C.class);
		            	    // lưu thông số để tính chunkReq
		            	    worldId    = hs.worldId();
		            	    tileSizeHs = hs.tileSize();
		            	    chunkSizeHs= hs.chunkSize();
		            	    viewDist   = hs.viewDist();
		            	    // gửi yêu cầu chunk đầu tiên ngay
		            	    lastCx = lastCy = null;
		            	    maybeRequestChunks();
		            	}case "chunk" -> {
		            	    ChunkS2C ch = Jsons.OM.treeToValue(node, ChunkS2C.class);
		            	    // giải mã bitset va chạm
		            	    byte[] rleBytes = Base64.getDecoder().decode(ch.collisionRLE());
		            	    BitSet solid = BitsetRLE.decode(rleBytes, ch.w() * ch.h());
		            	    // lưu cache tạm (chưa render chunk ở client)
		            	    long k = ckey(ch.cx(), ch.cy());
		            	    chunkTiles.put(k, ch.tiles());
		            	    chunkSolid.put(k, solid);
		            	    // không gọi repaint() ở đây
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
        	model.onInputSent(s, up,down,left,right, System.currentTimeMillis());
        	var msg = new InputC2S("input", s, new Keys(up,down,left,right));
        	ws.send(Jsons.OM.writeValueAsString(msg));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendClientPing(long ns) {
        try {
            ws.send(Jsons.OM.writeValueAsString(new ClientPingC2S(ns)));
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    public void sendAdmin(String token, String cmd) {
        try {
            ws.send(OM.writeValueAsString(java.util.Map.of(
                "type","admin","token", token, "cmd", cmd
            )));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
