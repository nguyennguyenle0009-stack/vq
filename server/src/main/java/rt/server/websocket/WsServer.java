package rt.server.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;

//Lớp WsServer chịu trách nhiệm khởi động WebSocket server bằng Netty.
//Nó quản lý vòng đời server (start/stop), accept kết nối client và chuyển dữ liệu
//tới handler (WsTextHandler).
public class WsServer {
	private static final Logger log = LoggerFactory.getLogger(WsServer.class);
	
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
	        .childOption(ChannelOption.TCP_NODELAY, true)
	        .childOption(ChannelOption.SO_KEEPALIVE, true)
	        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
	        	    new WriteBufferWaterMark(32 * 1024, 64 * 1024))
	    	.childHandler(new WsChannelInitializer(sessions, inputs));
	    serverChannel = b.bind(port).sync().channel();	// Bind server vào cổng và chạy đồng bộ (sync để block đến khi bind xong).
	    log.info("Server started at ws://localhost:{}/ws", port);
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

//Nếu sau này build bản web chạy ở domain khác, chỉ cần thêm domain vào allowedOrigins ở nơi khởi tạo pipeline.
