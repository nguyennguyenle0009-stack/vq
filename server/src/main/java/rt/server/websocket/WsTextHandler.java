package rt.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import rt.server.InputEvent;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;

import java.util.Map;
import java.util.UUID;

import vq.common.Packets;

public class WsTextHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final ObjectMapper OM = new ObjectMapper();

    private final SessionRegistry sessions;
    private final InputQueue inputs;
    private SessionRegistry.Session sess;

    public WsTextHandler(SessionRegistry sessions, InputQueue inputs){
        this.sessions = sessions; this.inputs = inputs;
    }

    @Override 
    public void handlerAdded(ChannelHandlerContext ctx) {
        sess = new SessionRegistry.Session(ctx.channel(), UUID.randomUUID().toString());
        sessions.attach(sess);
        sess.send(new Packets.S2CHello(sess.playerId)); // chào client
        System.out.println("Client connected: " + sess.playerId);
    }

    @Override 
    public void handlerRemoved(ChannelHandlerContext ctx) {
        System.out.println("Client disconnected: " + sess.playerId);
        sessions.detach(ctx.channel());
    }

    @Override 
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        var node = OM.readTree(msg.text());
        var op = node.path("op").asText("");
        if ("input".equals(op)) {
            int seq = node.path("seq").asInt();
            // press -> ax,ay   (right-left, down-up)
            Map<String,Boolean> press = OM.convertValue(node.path("press"),
                    new TypeReference<Map<String,Boolean>>() {});
            int ax = (isTrue(press,"right")?1:0) - (isTrue(press,"left")?1:0);
            int ay = (isTrue(press,"down")?1:0) - (isTrue(press,"up")?1:0);

            // đưa vào hàng đợi để game loop xử lý ở tick kế
            inputs.offer(new InputEvent(sess.playerId, seq, ax, ay));

            // gửi ack cho client (phục vụ reconciliation)
            sess.send(new Packets.S2CAck(seq, System.currentTimeMillis()));
        }
    }

    private static boolean isTrue(Map<String,Boolean> m, String k){
        return m!=null && Boolean.TRUE.equals(m.get(k));
    }

    @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace(); ctx.close();
    }
}