gamevq — Java Gradle multi-module (client / server / lib)

Dự án khởi tạo cho game tiên hiệp theo mô hình client–server.
Tách thành 3 module để dễ quản lý và đóng gói độc lập:

gamevq/
├─ client/   # Game client (UI, input, networking)
├─ server/   # Game server (DB, logic authoritative)
└─ common/   # Mã dùng chung (model, DTO, rule tính chỉ số, v.v.)


JDK: 17 • Build: Gradle (Kotlin DSL) • IDE: Eclipse

1) Yêu cầu môi trường

JDK 17 (Temurin/OpenJDK).

Gradle Wrapper đã có sẵn (gradlew, gradlew.bat), không cần cài Gradle.

(Tuỳ chọn) Git + Git LFS nếu có asset nặng (ảnh/âm thanh).

2) Cấu trúc thư mục tiêu chuẩn Gradle

Mỗi module đều theo chuẩn:

<module>/
└─ src/
   ├─ main/
   │  ├─ java/       # code chạy thật (bắt buộc)
   │  └─ resources/  # tài nguyên đi kèm jar (ảnh, cấu hình, SQL, ...)
   └─ test/
      ├─ java/       # test tự động (không bắt buộc lúc đầu)
      └─ resources/  # tài nguyên dùng riêng cho test
      
<Module server>/
server/
 └─ src/main/java/rt/server/
    ├─ main/
    │	├─ MainServer.java             // Điểm vào duy nhất
    ├─ websocket/
    │   ├─ WsServer.java           // bootstrap Netty
    │   ├─ WsInitializer.java      // pipeline
    │   └─ WsTextHandler.java      // xử lý text frames
    ├─ game/
    │	└─loop/
    │   	├─ GameLoop.java           // 60 TPS
    │   	├─ SnapshotStreamer.java   // 10–15 Hz đẩy state
    │   	└─ SnapshotBuffer.java
    ├─ world/
    │   └─ World.java              // game state, step(dt), capture(tick)
    ├─ session/
    │   └─ SessionRegistry.java    // quản lý kết nối + send JSON
    ├─ input/
    │   └─ InputQueue.java         // hàng đợi InputEvent (record)
    ├─ game/
    │	└─model/                      // nếu cần tách player, entity, map...
    │   	└─ PlayerState.java
    ├─ game/
    │	└─infra/                      // DB, Redis, logging… (để sau)
    └─ config/
        └─ ServerConfig.java       // cổng, TPS, HZ, kích thước map…
        
# Tính năng     

## Client

	in ra file log nếu gặp lỗi log failed to connect ....\Desktop\Vương quyền\....
	
	Nhấn F1 → console client: [ADMIN] OK - sessions: …
	Nhấn F2 → teleport chính mình về (5,5) nếu tile trống
	Nhấn F3 → reload map từ mapResourcePath	
    Nhấn F4 → hiện Dev HUD
	
## Server
	
# chạy server
	Xoá tay build/ và bin/ (hoặc chạy ./gradlew clean).
	Eclipse: Project → Clean…
	Gradle → Refresh Project
	
	gradlew --stop
	gradlew clean
	gradlew build
	gradlew :server:run
	# cửa sổ 2
	gradlew :client:run --args="A"
	# cửa sổ 3
	gradlew :client:run --args="B"

# Phiên bản

## 1.0.1

	Client: render + bắt phím + gửi input
	Server: snapshot + ping

## 1.0.2

	Client: Mượt chuyển động (client interpolation 100ms)
	Server: Dọn disconnect & path sai (server silent, không spam)
	
## 1.0.3
	
	Client-side prediction + reconciliation: áp input local tức thì, lưu pending, khi nhận ack/state thì “replay” các input > ack. (Giảm trễ cảm giác.)
	
	Client: 
		Model client: buffer snapshot + interpolation (tile) + prediction & reconciliation theo tile, client sẽ nhân 32px khi vẽ.
		Gắn prediction hooks: onInputSent(), onAck(), reconcileFromServer(); spawn sớm tại (3,3) tile sau hello.
	Server: Server dùng đơn vị tile hoàn toàn. Spawn mặc định (3,3) tiles. copyForNetwork trả x,y theo tile
	Common: Định nghĩa chung: tile 32px, kích thước world (theo ô), tốc độ theo ô/giây
	
## 1.0.4

	Mượt thêm xíu
	
## 1.0.5

	Hiễn thị FPS và Ping ra màn hình
	
## 1.0.6

	Backpressure & rate-limit: server drop input nếu > 60/s/người; SnapshotStreamer chỉ giữ state mới nhất khi kênh chậm.
	
	Rate-limit input và backpressure cho streamer
		Rate-limit input: tối đa 60 gói/giây/người
		Backpressure cho SnapshotStreamer: chỉ gửi state MỚI NHẤT khi kênh chậm
	
	Test nhanh
		Client spam input (giữ phím nhấp nhả nhanh):
		Server không tăng CPU, vẫn mượt.
		Nhân vật vẫn đi đúng do world đọc “latest keys”.
		Làm client “chậm” (tăng tải, thu nhỏ/di chuyển cửa sổ):
		Không backlog state; khi hồi, nhận ngay state mới nhất (không nhảy giật).
	
## 1.0.7

	tránh “nhảy giật” khi JVM trễ, dùng vòng lặp có catch-up (giới hạn số bước bù)
	“chốt số”
		TPS = 60, snapshotHz = 20.
		MAX_CATCHUP_STEPS = 2 (bắt đầu), nếu vẫn trễ ngắn thì 3.
		Client INTERP_DELAY_MS = 100–120 ms (hợp với 20 Hz).
		Rate-limit input 60/s (đã có).
		Netty: TCP_NODELAY=true, WRITE_BUFFER_WATER_MARK(32k,64k).
	Note: Bản này giúp mượt hơn khi có “tụt nhịp” ngắn (GC, spike CPU), nhưng không để server rơi vào vòng bù vô tận.

## 1.0.8

	Protocol sạch hơn: gom message thành DTO ở :common (đang parse kiểu Map), dùng ObjectMapper tái sử dụng.

## 1.0.9

	Bảo mật cơ bản: giới hạn kích thước frame (WebSocketServerProtocolHandler), check Origin, ẩn stacktrace DEBUG.
	Giới hạn kích thước WebSocket frame (chống spam/DoS)
	Check Origin (chặn WebSocket từ domain lạ nếu chạy trong trình duyệt)
	Ẩn stacktrace ồn ào (chỉ bật khi DEBUG)

## 1.0.10
	
	Tách pipeline riêng: WsChannelInitializer
	Gắn initializer vào server bootstrap
	Kiểm tra nhanh
		gradlew clean :server:run Console phải in Server started at ws://localhost:<port>/ws.
		Mở client như thường lệ.
		Thử đóng client đột ngột → server không còn stacktrace dài; tối đa log connection reset gọn.
		Nếu mở từ browser khác origin → bị 403 (đã chặn Origin).
	
## 1.0.11

	GameCanvas panel mới có grid cache, ít cấp phát, HUD FPS/Ping.
	Sửa ClientApp để dùng render loop 60 FPS (thread riêng gọi repaint()), giữ input 30 Hz + cping 1 s.
		Thay phần tạo panel/timer trong
	Ghi chú hiệu năng
		Grid cache: chỉ vẽ lại khi đổi kích thước cửa sổ → giảm rất nhiều thời gian paint.
		Ít cấp phát: paintComponent không tạo Set/Map tạm; chỉ duyệt snapshot trả về từ WorldModel.
		Render loop bằng System.nanoTime() giúp nhịp ổn định hơn Swing Timer.
		Nếu muốn giảm thêm GC: về sau có thể thêm API trong WorldModel để fill vào Map tái sử dụng thay vì tạo Map mới mỗi snapshot (không bắt buộc ngay).

## 1.0.12
	
	Cấu hình linh hoạt. 
	Đưa mọi tham số WS/pipeline ra ServerConfig và truyền xuống WsServer → WsChannelInitializer thay vì hardcode
	ServerConfig mở rộng (JSON, không cần thêm lib)
	WsChannelInitializer nhận config
	WsServer truyền config xuống & set socket options từ config
	Main khởi động
	Lợi ích
		Không cần sửa code khi đổi port, path, giới hạn frame, origin, idle…
		Dễ bật/tắt Origin check, đổi watermarks, extensions.
		Hợp nhất cấu hình vào một nơi (ServerConfig) → sạch & nhất quán.

## 1.0.13

	Chốt protocol + capture state
		Acceptance: streamer chỉ gọi 1 API world.capture, client nhận ver=1, không còn convert Map thô.
	Map tile + collision tối thiểu
		Acceptance: nhân vật không đi xuyên tường, clamp theo map, không còn clamp theo kích thước cửa sổ.
	Test đơn vị (JUnit 5) cho core
		Acceptance: chạy gradlew :server:test pass; có test cho clamp, normalize, reconcile.

## 1.0.14

	Client nhận & vẽ map (tường) từ server
	HUD hiển thị tick & số entity (bổ sung vào WorldModel + GameCanvas)
	Dọn log trùng “Server started …”
	
## 1.0.15

	Mục tiêu:
		Thêm ErrorS2C (server báo lỗi chuẩn cho client)
		Admin command tối giản qua WS (token trong config): listSessions, teleport, reloadMap
		Rate-limit input kèm thông báo lỗi (drop > 60 input/s/người, báo tối đa 1 lần/giây)
		Bổ sung cấu hình: adminToken, mapResourcePath
		Trong channelRead0(...) – switch (type)
			Hello: (giữ như hiện tại) + đã có gửi map ở vòng trước; không đổi.	
			Input: chèn rate-limit trước khi inputs.offer(...).
			Admin: parse, check token và thực thi lệnh; trả AdminResultS2C.
			Bỏ warn cho cpong/pong như trước (đã làm).
	Test nhanh
		Rate-limit: giữ phím di chuyển và (tạm) tăng tần suất gửi input ở client lên 10–15ms một lần → console client sẽ nhận error RATE_LIMIT_INPUT ~ mỗi giây một lần (không spam), server vẫn mượt vì drop phần dư.
		Admin (từ client A):
		Gửi JSON thủ công (tạm thời): bạn có thể thêm nút dev hoặc dùng tạm ws.send(...) trong client:
		ws.send(OM.writeValueAsString(Map.of("type","admin","token","dev-secret-123","cmd","listSessions")));
		ws.send(OM.writeValueAsString(Map.of("type","admin","token","dev-secret-123","cmd","teleport <id> 10 4")));
		ws.send(OM.writeValueAsString(Map.of("type","admin","token","dev-secret-123","cmd","reloadMap")));
		Console client sẽ in [ADMIN] OK - ....
		Map reload: sửa file maps/test01.json (thêm bức tường), gọi reloadMap, kiểm tra va chạm thay đổi ngay (state mới giữ nguyên, chỉ collision thay đổi).
	bước kế tiếp (sau khi patch này OK)
		Thêm ErrorS2C cho các lỗi khác: PAYLOAD_TOO_LARGE, BAD_SCHEMA, ORIGIN_FORBIDDEN (nếu check origin bật).
		Nhẹ nhàng refactor InputQueue để rate-limit nằm trong hàng đợi (thay vì ở handler) nếu bạn muốn tách biệt network & logic.
		Thêm HUD Dev Toggle (F3) để bật/tắt hiển thị: tick, ents, fps, ping, rate-drop count.
		(Khi cần) loader TMX (Tiled) cho map phức tạp.
		
## 1.0.16

	Thêm ErrorS2C cho các lỗi khác: PAYLOAD_TOO_LARGE, BAD_SCHEMA, ORIGIN_FORBIDDEN (nếu check origin bật).
	Nhẹ nhàng refactor InputQueue để rate-limit nằm trong hàng đợi (thay vì ở handler) nếu bạn muốn tách biệt network & logic.
	Thêm HUD Dev Toggle (F3) để bật/tắt hiển thị: tick, ents, fps, ping, rate-drop count.
	(Khi cần) loader TMX (Tiled) cho map phức tạp.
	Test nhanh
		Giữ phím di chuyển thật nhanh → mỗi ~1s client sẽ nhận [SERVER ERROR] RATE_LIMIT_INPUT … (không spam), game vẫn mượt.
		Nhấn F1 → console client: [ADMIN] OK - sessions: …
		Nhấn F2 → teleport chính mình về (5,5) nếu tile trống
		Nhấn F3 → reload map từ mapResourcePath		
		
## 1.0.17

	Chuẩn hoá Error codes: BAD_SCHEMA, PAYLOAD_TOO_LARGE, ORIGIN_FORBIDDEN, ADMIN_UNAUTHORIZED.
		common - thêm hằng số mã lỗi
		server - OriginCheck gửi error & close
		server – Bắt TooLongFrameException → PAYLOAD_TOO_LARGE
		server – dùng ErrorCodes trong WsTextHandler
		
## 1.0.18

	HUD Dev (F4): tick, ents, dropped inputs, streamer skips, pending inputs
		common – DTO dev stats
		server – đếm & gửi dev stats
			Ghi nhận counters trong session
			Khi rate-limit trong WsTextHandler → tăng counter
			Đếm streamer skips & gửi DevStatsS2C mỗi giây
		client – lưu & vẽ HUD Dev (F4)
			WorldModel: lưu dev stats + expose pending size
			NetClient: nhận “dev_stats”
			GameCanvas: toggle F4 & vẽ HUD Dev
			
## 1.0.19

	Loader TMX (Tiled) – tuỳ lúc cần (đặt sẵn)
		loader đọc Tiled JSON (không phải TMX XML) vì Tiled xuất JSON tiện hơn.

## 1.0.20




















# FixBug


