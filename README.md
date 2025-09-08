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


# FixBug


