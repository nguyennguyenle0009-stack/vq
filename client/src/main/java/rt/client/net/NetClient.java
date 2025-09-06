package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import com.fasterxml.jackson.databind.ObjectMapper;
import rt.client.model.WorldModel;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Lớp chịu trách nhiệm WebSocket: connect, gửi input, nhận hello/ack/state. */
public class NetClient {
    private final String url;
    private final WorldModel model;
    private final ObjectMapper OM = new ObjectMapper();
    private WebSocket ws;
    private int lastSeq = 0;

    public NetClient(String url, WorldModel model) {
        this.url = url; this.model = model;
    }

    public void connect() {
        OkHttpClient http = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        Request req = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                System.out.println("[NET] WS open");
            }
            @Override public void onMessage(WebSocket webSocket, String text) { handleMessage(text); }
            @Override public void onMessage(WebSocket webSocket, ByteString bytes) { /* not used */ }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("[NET] WS closed: " + reason);
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                System.out.println("[NET] WS failure");
                t.printStackTrace();
            }
        });
    }

    public int nextSeq(){ return ++lastSeq; }

    /** Gửi input press map. */
    public void sendInput(int seq, Map<String, Boolean> press) {
        try {
            var node = Map.of("op","input","seq",seq,"press",press);
            ws.send(OM.writeValueAsString(node));
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Nhận hello/ack/state và đẩy vào model. */
    private void handleMessage(String text) {
        try {
            var n = OM.readTree(text);
            String op = n.path("op").asText("");
            switch (op) {
                case "hello" -> {
                    String pid = n.path("playerId").asText();
                    model.setMyId(pid);
                    System.out.println("[NET] HELLO id=" + pid);
                }
                case "ack" -> {
                    int seq = n.path("seq").asInt();
                    // chừa chỗ reconciliation nếu cần
                    // System.out.println("[NET] ACK " + seq);
                }
                case "state" -> {
                    long tick = n.path("tick").asLong();
                    var you = n.path("you");
                    var entsNode = n.path("ents");

                    // your position (authority từ server)
                    double yx = you.path("x").asDouble();
                    double yy = you.path("y").asDouble();
                    model.updateMe(yx, yy);

                    // others map
                    Map<String, WorldModel.Player> ents = new java.util.HashMap<>();
                    var it = entsNode.fields();
                    while (it.hasNext()) {
                        var e = it.next();
                        var pNode = e.getValue();
                        var p = new WorldModel.Player();
                        p.x = pNode.path("x").asDouble();
                        p.y = pNode.path("y").asDouble();
                        ents.put(e.getKey(), p);
                    }
                    model.applyStateFromServer(tick, ents);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
