package rt.server.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateHandler;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class WsChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final SessionRegistry sessions;
    private final InputQueue inputs;

    public WsChannelInitializer(SessionRegistry sessions, InputQueue inputs) {
        this.sessions = sessions;
        this.inputs = inputs;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        // HTTP handshake
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(8 * 1024)); // đủ cho handshake/headers

        // Kiểm tra Origin (nếu có header, web browser). Desktop client thường không có -> cho qua.
        p.addLast(new OriginCheckHandler(Set.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080"
        )));

        // WebSocket + limit frame
        WebSocketServerProtocolConfig cfg = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/ws")
                .handshakeTimeoutMillis(10_000)
                .allowExtensions(false)               // tắt permessage-deflate cho an toàn
                .maxFramePayloadLength(64 * 1024)     // giới hạn 64KB/frame
                .checkStartsWith(false)
                .build();
        p.addLast(new WebSocketServerProtocolHandler(cfg));

        // (tuỳ chọn) gộp continuation frames (vẫn bị giới hạn bởi maxFramePayloadLength)
        p.addLast(new WebSocketFrameAggregator(64 * 1024));

        // (tuỳ chọn) đóng kết nối nếu 45s không có inbound
        p.addLast(new IdleStateHandler(45, 0, 0, TimeUnit.SECONDS));

        // Handler nghiệp vụ
        p.addLast(new WsTextHandler(sessions, inputs));
    }
}
