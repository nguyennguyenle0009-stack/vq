package rt.server.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;

public class WsServer {
    private final int port;
    private final SessionRegistry sessions; private final InputQueue inputs;

    public WsServer(int port, SessionRegistry s, InputQueue i){ this.port = port; this.sessions = s; this.inputs = i; }

    public void start() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        var b = new ServerBootstrap()
            .group(boss, worker)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(65536));
                    p.addLast(new WebSocketServerProtocolHandler("/ws", null, true));
                    p.addLast(new WsTextHandler(sessions, inputs));
                }
            });
        b.bind(port).sync();
    }
}
