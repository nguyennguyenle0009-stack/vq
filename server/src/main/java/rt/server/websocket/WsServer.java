package rt.server.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
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
	  private final SessionRegistry sessions;
	  private final InputQueue inputs;
	  private EventLoopGroup bossGroup;
	  private EventLoopGroup workerGroup;
	  private Channel serverChannel;

	  public WsServer(int port, SessionRegistry s, InputQueue i) {
	    this.port = port; this.sessions = s; this.inputs = i;
	  }

	  public void start() throws InterruptedException {
	    bossGroup = new NioEventLoopGroup(1);
	    workerGroup = new NioEventLoopGroup();
	    ServerBootstrap b = new ServerBootstrap()
	        .group(bossGroup, workerGroup)
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
	    serverChannel = b.bind(port).sync().channel();
	  }

	  public void stop() {
	    try { if (serverChannel != null) serverChannel.close().sync(); } catch (InterruptedException ignored) {}
	    if (bossGroup != null) bossGroup.shutdownGracefully();
	    if (workerGroup != null) workerGroup.shutdownGracefully();
	  }
	}
