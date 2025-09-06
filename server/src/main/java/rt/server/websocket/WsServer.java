package rt.server.websocket;

import io.netty.channel.socket.SocketChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class WsServer {
    private final int port;
    public WsServer(int port) { this.port = port; }

    public void start() throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1);   // Nhận kết nối mới
        EventLoopGroup worker = new NioEventLoopGroup(); // Xử lý I/O

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec()); // decode HTTP request
                    p.addLast(new HttpObjectAggregator(65536)); // gom HTTP thành 1 request
                    // "/ws" là endpoint WebSocket
                    p.addLast(new WebSocketServerProtocolHandler("/ws", null, true));
                    // Handler cuối: xử lý message text
                    p.addLast(new WsTextHandler());
                }
            });
            b.bind(port).sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
