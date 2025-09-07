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

    public NetClient(String url, WorldModel model) { this.url = url; this.model = model; }

    public void connect(String playerName) {
        OkHttpClient http = new OkHttpClient.Builder()
        	    .connectTimeout(5, TimeUnit.SECONDS)
        	    .readTimeout(0, TimeUnit.MILLISECONDS)
        	    .pingInterval(30, TimeUnit.SECONDS)   // OkHttp tự gửi PING; server có thể trả PONG
        	    .retryOnConnectionFailure(false)
        	    .build();
        Request req = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(req, new WebSocketListener() {
            @Override 
            public void onOpen(WebSocket webSocket, Response response) {
                // Bình thường code phải là 101 Switching Protocols
                if (response != null && response.code() != 101) {
                    System.err.println("[NET] Unexpected handshake code " + response.code()
                        + " " + response.message() + " url=" + url);
                }
                try {
                    ws.send(OM.writeValueAsString(Map.of("type","hello","name",playerName)));
                    System.out.println("[NET] open, hello sent");
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override 
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    Map<String,Object> root = OM.readValue(text, new TypeReference<Map<String,Object>>(){});
                    String type = (String) root.get("type");
                    if ("hello".equals(type)) {
                        String you = (String) root.get("you");
                        model.setYou(you);
                        System.out.println("[NET] hello ok, you=" + you);
                    } else if ("ack".equals(type)) {
                        // có thể hiển thị số seq nếu muốn
                    } else if ("state".equals(type)) {
                        model.applyState(root);
                    } else if ("ping".equals(type)) {
                        ws.send(OM.writeValueAsString(Map.of("type","pong","ts", System.currentTimeMillis())));
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override 
            public void onMessage(WebSocket webSocket, ByteString bytes) {}

            @Override 
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("[NET] closed: " + code + " " + reason + " url=" + url);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String msg;
                if (response != null) {
                    // Sai path → thường là HTTP 404/400; TLS/upgrade fail → 403/426/…
                    msg = "HTTP " + response.code() + " " + response.message();
                    response.close();
                } else {
                    msg = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
                }
                System.err.println("[NET] WebSocket failure: " + msg + " url=" + url);
            }
        });
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right) {
        try {
            int s = seq.incrementAndGet();
            Map<String,Object> msg = Map.of(
                "type","input",
                "seq", s,
                "keys", Map.of("up",up,"down",down,"left",left,"right",right)
            );
            ws.send(OM.writeValueAsString(msg));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
