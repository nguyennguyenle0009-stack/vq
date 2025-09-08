package rt.client.net;

import okhttp3.*;
import okio.ByteString;
import rt.client.model.WorldModel;
import rt.common.net.Jsons;
import rt.common.net.dto.*;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

public class NetClient {
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
            @Override public void onOpen(WebSocket webSocket, Response response) {
                try {
                    ws.send(Jsons.OM.writeValueAsString(new HelloC2S(playerName)));
                    System.out.println("[NET] open, hello sent");
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode node = Jsons.OM.readTree(text);
                    String type = node.path("type").asText(null);
                    if (type == null) return;

                    switch (type) {
                        case "hello" -> {
                            HelloS2C msg = Jsons.OM.treeToValue(node, HelloS2C.class);
                            model.setYou(msg.you());
                            model.spawnAt(3, 3); // xuất hiện ngay
                            System.out.println("[NET] hello ok, you=" + msg.you());
                        }
                        case "ack" -> {
                            AckS2C a = Jsons.OM.treeToValue(node, AckS2C.class);
                            lastAck = a.seq();
                            model.onAck(a.seq());
                        }
                        case "state" -> {
                            StateS2C st = Jsons.OM.treeToValue(node, StateS2C.class);

                            // Convert sang Map để giữ nguyên WorldModel.applyState(Map<...>) hiện tại
                            Map<String, Object> entsMap = new HashMap<>();
                            st.ents().forEach((id, es) ->
                                    entsMap.put(id, Map.of("x", es.x(), "y", es.y()))
                            );
                            Map<String, Object> root = Map.of(
                                    "type", "state",
                                    "ts", st.ts(),
                                    "ents", entsMap
                            );
                            model.applyState(root);

                            // Reconcile YOU
                            var you = model.you();
                            if (you != null) {
                                var me = st.ents().get(you);
                                if (me != null) model.reconcileFromServer(me.x(), me.y(), lastAck);
                            }
                        }
                        case "ping" -> {
                            PingS2C pg = Jsons.OM.treeToValue(node, PingS2C.class);
                            // trả "pong" với đúng ts server gửi, để server đo RTT server-side
                            ws.send(Jsons.OM.writeValueAsString(new PongC2S(pg.ts())));
                        }
                        case "cpong" -> {
                            ClientPongS2C cp = Jsons.OM.treeToValue(node, ClientPongS2C.class);
                            if (onClientPong != null) onClientPong.accept(cp.ns()); // HUD Ping (client-side)
                        }
                        default -> { /* ignore */ }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override public void onMessage(WebSocket webSocket, ByteString bytes) { /* no-op */ }
        });
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right) {
        try {
            int s = seq.incrementAndGet();
            long now = System.currentTimeMillis();
            model.onInputSent(s, up, down, left, right, now);
            ws.send(Jsons.OM.writeValueAsString(new InputC2S(s, new Keys(up, down, left, right))));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendClientPing(long ns) {
        try {
            ws.send(Jsons.OM.writeValueAsString(new ClientPingC2S(ns)));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
