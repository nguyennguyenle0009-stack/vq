package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import rt.client.model.MapModel;
import rt.client.model.WorldModel;

import rt.common.dto.*;
import rt.common.json.Jsons;
import rt.common.net.dto.ClientPingC2S;

import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
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
    public void setOnClientPong(LongConsumer cb) { this.onClientPong = cb; }

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
                	var hello = new HelloC2S("hello", playerName);
                	ws.send(Jsons.OM.writeValueAsString(hello));
                } catch (Exception e) { e.printStackTrace(); }
            }

			@Override 
			public void onMessage(WebSocket webSocket, String text) {
			  try {
				  JsonNode node = Jsons.OM.readTree(text);
				  String type = node.hasNonNull("type") ? node.get("type").asText() : "";
				  switch (type) {
				      case "hello" -> {
				          HelloS2C m = Jsons.OM.treeToValue(node, HelloS2C.class);
				          model.setYou(m.you());
				          // Hiện chấm ngay khi chưa kịp state
				          model.spawnAt(3,3);
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
				          }
				      }
				      case "dev_stats" -> {
				          DevStatsS2C ds = Jsons.OM.treeToValue(node, DevStatsS2C.class);
				          model.applyDevStats(ds.droppedInputs(), ds.streamerSkips(), ds.writable(), ds.ents());
				      }
				      case "error" -> {
				          ErrorS2C er = Jsons.OM.treeToValue(node, ErrorS2C.class);
				          log.warn("server error {}: {}", er.code(), er.message());
				      }
				      case "ping" -> {
				          long ts = node.hasNonNull("ts") ? node.get("ts").asLong() : System.currentTimeMillis();
				          long now = System.currentTimeMillis();
				          model.setPingMs(Math.max(0, now - ts)); // RTT ước lượng (nếu server gửi ts của server, coi như đo “one-way”)
				          ws.send(Jsons.OM.writeValueAsString(new PongC2S("pong", now)));
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
        	var msg = new InputC2S("input", s, new InputC2S.Keys(up,down,left,right));
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
