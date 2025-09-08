package rt.server.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
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
	            p.addLast(new HttpObjectAggregator(8 * 1024));	// gộp HTTP message thành full request
	            // Kiểm tra Origin trước khi upgrade
	            p.addLast(new rt.server.websocket.OriginCheckHandler(
	                    java.util.Set.of(
	                        "http://localhost:8080",  // thêm domain bạn cho phép
	                        "http://127.0.0.1:8080"
	                    )
	            ));
	            // Cấu hình WebSocket: path, tắt extensions, giới hạn payload
	            io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig cfg =
	                    io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig.newBuilder()
	                        .websocketPath("/ws")
	                        .subprotocols(null)
	                        .checkStartsWith(false)
	                        .handshakeTimeoutMillis(10_000)
	                        .allowExtensions(false)                 // tắt permessage-deflate để đơn giản & an toàn
	                        .maxFramePayloadLength(64 * 1024)       // iới hạn 64 KB / frame
	                        .build();
	            p.addLast(new io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler(cfg));
	            p.addLast(new io.netty.handler.timeout.IdleStateHandler(45, 0, 0, java.util.concurrent.TimeUnit.SECONDS));

	            // (tuỳ chọn) gộp continuation frames nhưng vẫn tuân maxFramePayloadLength ở trên
	            p.addLast(new io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator(64 * 1024));

	            // Handlers
	            p.addLast(new rt.server.websocket.WsTextHandler(sessions, inputs)); // handler xử lý WebSocket frame dạng text do ta viết
	        	}
	        })
	        .childOption(ChannelOption.TCP_NODELAY, true)
	        .childOption(ChannelOption.SO_KEEPALIVE, true)
	        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
	        	    new io.netty.channel.WriteBufferWaterMark(32 * 1024, 64 * 1024));
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

//Nếu sau này build bản web chạy ở domain khác, chỉ cần thêm domain vào allowedOrigins ở nơi khởi tạo pipeline.
