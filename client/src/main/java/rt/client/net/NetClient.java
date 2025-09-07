package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import rt.client.model.WorldModel;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NetClient {
    private final String url;
    private final WorldModel model;
    private final ObjectMapper OM = new ObjectMapper();
    private WebSocket ws;
    private final AtomicInteger seq = new AtomicInteger();
    private volatile int lastAck = 0;

    public NetClient(String url, WorldModel model) { this.url = url; this.model = model; }

    public void connect(String playerName) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();

        Request req = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    ws.send(OM.writeValueAsString(Map.of("type","hello","name",playerName)));
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    Map<String,Object> root = OM.readValue(text, new TypeReference<Map<String,Object>>(){});
                    String type = (String) root.get("type");
                    switch (type) {
                        case "hello" -> {
                            String you = (String) root.get("you");
                            model.setYou(you);
                            model.spawnAt(3,3); // vẽ sớm tại (3,3) tiles
                        }
                        case "ack" -> {
                            int a = ((Number) root.getOrDefault("seq", 0)).intValue();
                            lastAck = a;
                            model.onAck(a);
                        }
                        case "state" -> {
                            model.applyState(root);
                            String you = model.you();
                            if (you != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> ents = (Map<String, Object>) root.get("ents");
                                if (ents != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> me = (Map<String, Object>) ents.get(you);
                                    if (me != null) {
                                        double sx = ((Number) me.getOrDefault("x", 0)).doubleValue();
                                        double sy = ((Number) me.getOrDefault("y", 0)).doubleValue();
                                        model.reconcileFromServer(sx, sy, lastAck);
                                    }
                                }
                            }
                        }
                        case "ping" -> {
                            ws.send(OM.writeValueAsString(Map.of("type","pong","ts", System.currentTimeMillis())));
                        }
                        default -> { /* ignore */ }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {}
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {}
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response r) {
                String msg = (r!=null) ? ("HTTP "+r.code()+" "+r.message())
                        : (t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
                System.err.println("[NET] fail: " + msg + " url=" + url);
            }
        });
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right) {
        try {
            int s = seq.incrementAndGet();
            long now = System.currentTimeMillis();
            model.onInputSent(s, up, down, left, right, now);
            Map<String,Object> msg = Map.of(
                "type","input",
                "seq", s,
                "keys", Map.of("up",up,"down",down,"left",left,"right",right)
            );
            ws.send(OM.writeValueAsString(msg));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
