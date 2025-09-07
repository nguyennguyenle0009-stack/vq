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

//Lớp WsServer chịu trách nhiệm khởi động WebSocket server bằng Netty.
//Nó quản lý vòng đời server (start/stop), accept kết nối client và chuyển dữ liệu
//tới handler (WsTextHandler).
public class WsServer {
	  private final int port; // Cổng mà server sẽ lắng nghe WebSocket (ví dụ: 8080).
	  private final SessionRegistry sessions; // Quản lý danh sách phiên (session) của các client kết nối.
	  private final InputQueue inputs; // Hàng đợi input từ client gửi lên (để game loop xử lý).
	  private EventLoopGroup bossGroup; // Nhóm thread quản lý kết nối "chấp nhận socket" (boss).
	  private EventLoopGroup workerGroup; // Nhóm thread xử lý I/O cho từng kết nối (worker).
	  private Channel serverChannel; // Channel đại diện cho server socket (cổng WebSocket).

	  // Constructor: khởi tạo server với cổng, registry quản lý session và input queue.
	  public WsServer(int port, SessionRegistry s, InputQueue i) {
	    this.port = port; this.sessions = s; this.inputs = i;
	  }

	  // Bắt đầu chạy server WebSocket.
	  public void start() throws InterruptedException {
	    bossGroup = new NioEventLoopGroup(1); // Nhóm boss: chỉ cần 1 thread để nhận kết nối mới.
	    workerGroup = new NioEventLoopGroup(); // Nhóm worker: thread pool xử lý đọc/ghi dữ liệu cho các client.
	    // Cấu hình server Netty.
	    ServerBootstrap b = new ServerBootstrap()
	        .group(bossGroup, workerGroup)	// gắn nhóm boss/worker
	        .channel(NioServerSocketChannel.class)	// kiểu channel cho server socket
	        .childHandler(new ChannelInitializer<SocketChannel>() {
	        	// Hàm này chạy khi có client mới kết nối.
	        	@Override
	        	protected void initChannel(SocketChannel ch) {
	            ChannelPipeline p = ch.pipeline();	// pipeline: chuỗi các handler
	            p.addLast(new HttpServerCodec());	// codec HTTP (vì WebSocket upgrade từ HTTP)
	            p.addLast(new HttpObjectAggregator(65536));	// gộp HTTP message thành full request
	            p.addLast(new WebSocketServerProtocolHandler("/ws", null, true));
	            p.addLast(new io.netty.handler.timeout.IdleStateHandler(45, 0, 0, java.util.concurrent.TimeUnit.SECONDS));
	            p.addLast(new WsTextHandler(sessions, inputs));	// handler xử lý WebSocket frame dạng text do ta viết
	          }
	        });
	    serverChannel = b.bind(port).sync().channel();	// Bind server vào cổng và chạy đồng bộ (sync để block đến khi bind xong).
	  }

	// Dừng server, giải phóng tài nguyên.
	public void stop() {
	try { 
		if (serverChannel != null) serverChannel.close().sync(); // đóng cổng server
	} 
	catch (InterruptedException ignored) {}
	    if (bossGroup != null) bossGroup.shutdownGracefully(); // tắt thread boss
	    if (workerGroup != null) workerGroup.shutdownGracefully();	// tắt thread worker
	}
}
