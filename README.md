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
# note

## Common (dùng chung world-gen)

	rt.common.world.ChunkPos.SIZE – kích thước 1 chunk (mặc định 64 tile). Phải trùng client & server.
	rt.common.world.WorldGenConfig
		seed – seed 64-bit của thế giới.
		plainRatio (mặc định 0.55), forestRatio (0.35) → tỉ lệ Plain/Forest trong lục địa; Desert = 1 − (plain+forest).
	rt.common.world.WorldGenerator (bản nền GĐ1)
		OCEAN_THRESHOLD ~ 0.35 (mask lục địa/biển).
		MOUNTAIN_THRESHOLD ~ 0.82 (điểm núi rải nhẹ, có collision).
		(Noise/hash mix cố định; không cần đụng nếu chưa tối ưu.)

## Server

	server-config.json (đọc ở ServerConfig.load()):
		port – cổng WS.
		adminToken (mặc định "dev-secret-123").
		tcpNoDelay, soKeepAlive – socket options.
		writeBufferLowKB, writeBufferHighKB – Netty watermarks (KB).
		worldSeed – nếu =0 sẽ rơi về mặc định (đang hard-code fallback 20250917L).
		mapResourcePath – đường dẫn map tĩnh (giữ cho mode cũ).
	Tick/stream:
		State broadcast ~20 Hz (client ước lượng 50 ms/snapshot). Nếu bạn có StateStreamer/scheduler riêng, biến Hz thường ở đó (chưa đổi trong đoạn bạn dán).
		Input apply: chạy trong World.step(dt).
	rt.server.world.World
		SPEED = 3.0 (tiles/s) – tốc độ “authoritative”.
		CHUNK_SIZE = ChunkPos.SIZE (đừng đổi lệch common).
		Spawn tìm đất:
			MACRO_STEP_CHUNKS = max(4, 12000/CHUNK_SIZE) – bước quét vòng.
			MAX_RINGS = 512 – số vòng quét tối đa.
	Chunk dịch vụ:
		rt.server.world.chunk.ChunkService – bơm chunk cho World + WsTextHandler.
	Gói chào seed → client:
		SeedS2C(seed, chunkSize, tileSize) – hiện đang gửi chunkSize=ChunkPos.SIZE, tileSize=32 (bạn có thể cho tileSize vào server-config.json nếu muốn chỉnh runtime).

## Client

	Tốc độ dự đoán (prediction): rt.common.game.Units.SPEED_TILES_PER_SEC = 3.75 (tiles/s).
	👉 NÊN ĐỂ TRÙNG với World.SPEED (ví dụ cả hai 4.0) để giảm drift (mình đã note việc này).
	Render & loop
		RenderLoop mục tiêu 60 FPS.
		ClientApp gửi input mỗi 33 ms (~30 Hz) & client-ping 1000 ms.
	Chunk streaming
		rt.client.world.ChunkCache.R – bán kính nạp (mặc định 1 → 3×3 chunk). Tăng lên 2 (=5×5) nếu máy khỏe.
		NetClient giữ:
			chunkSize (từ server), tileSize (từ server).
			seed – để đồng bộ world-gen client (nếu dùng vẽ client-side).
	Nội suy & HUD: rt.client.model.WorldModel
		interpDelayMs khởi tạo 100 ms (tự điều chỉnh theo nhịp snapshot).
		OFFSET_ALPHA 0.12 – EMA ước lượng clock offset server–client.
		PING_ALPHA 0.25 – EMA ping hiển thị.
		MAX_BUF 60 – số snapshot giữ trong buffer.
	Camera mượt (nếu bạn có center-cam):
		Hệ số lerp mình gợi ý 0.18 (đặt thành hằng CAMERA_LERP để tiện test).
	Hotkeys/HUD
		F4 toggle Dev HUD (ở GameCanvas/HudOverlay).
		AdminHotkeys dùng adminToken khi gửi lệnh.
		
## Màu sắc & hiển thị

	TileRenderer: bảng màu theo ID (đơn giản để test). Bạn có thể gom vào Theme/Palette.
		Gợi ý mapping hiện tại (dễ nhìn & tương phản, bạn sửa thoải mái):
			Water/Ocean (ID 0/1): #1f5fa8
			Plain (ID 2): #cfe6a0
			Forest (ID 3): #2e7d32
			Desert (ID 4): #e8df9a
			Mountain (ID 5): #6f6f6f
	GridRenderer: màu lưới & alpha (đường “chỉ đen”) – dùng 1 màu + alpha thấp (vd rgba(0,0,0,0.12)), hoặc tắt khi zoom xa.
	EntityRenderer: màu chấm người chơi (vd xanh lá #2aff3b) + nhãn màu trắng.
	
## Những thứ cần đồng bộ (đổi 1 nơi phải đổi nơi kia)

	ChunkPos.SIZE, chunkSize – common/server/client.
	Tốc độ di chuyển: World.SPEED (server) = Units.SPEED_TILES_PER_SEC (client).
	tileSize – server gửi cho client (đang là 32).
	seed – duy nhất cho phiên (server phát, client dùng đúng).

# Tính năng     

## Client

	in ra file log nếu gặp lỗi log failed to connect ....\Desktop\Vương quyền\....
	
	Nhấn F1 → console client: [ADMIN] OK - sessions: …
	Nhấn F2 → teleport chính mình về (5,5) nếu tile trống
	Nhấn F3 → reload map từ mapResourcePath	
    Nhấn F4 → hiện Dev HUD
    Nhấn M  → hiện bản đồ
	
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
		
	fixbug: Connection reset tắt client đột ngột 

## 1.0.20
	
	Tách Netty dependency: thay netty-all bằng netty-handler, netty-codec-http, netty-transport, netty-buffer.

## 1.0.21

	Mục tiêu, 
		DTO-first ở client (bỏ Map tạm trong onMessage) → sạch schema, ít bug.
		Tests cho prediction/reconcile, rate-limit, streamer backpressure.
		CI GitHub Actions (JDK 17) để PR nào cũng build/test tự động.
		Fat-jar server + script chạy nhanh (scripts/run-server.(bat|sh)).
		Dev HUD (F4) hiển thị đầy đủ: tick, ents(server/render), pending, dropped, streamer skips, writable.

	Cách test, và thay đổi hành vi
		DTO-first ở client:
			Client không còn parse Map thô trong onMessage, mà dùng các DTO (HelloS2C, StateS2C, …). Điều này giảm bug schema và dễ đọc code hơn. Server của bạn đang gửi các kiểu hello/state/ack/ping/dev_stats → client parse khớp.
			Test: chạy ./gradlew :client:test và :client:run --args="A" → nhân vật vẫn di chuyển như trước; mọi thứ mượt như cũ.
		Dev HUD (F4):
			Nhấn F4 để xem ô HUD: FPS, Ping, Tick(render est), Ents(server/render), Pending, Dropped, Streamer skips, Writable.
			Test: bật/tắt F4 khi đang chơi; tắt/đổi focus cửa sổ để thấy FPS thay đổi; nếu server gửi dev_stats, HUD sẽ hiện đầy đủ.
		Tests:
			WorldPredictionTest kiểm chứng dự đoán client + reconcile.
			StreamerBackpressureTest xác minh triết lý “giữ state mới nhất” (last-write-wins) bằng slot AtomicReference.
			RateLimitSketchTest là phác thảo; nếu InputQueue đã có counter, bạn có thể đổi test trỏ trực tiếp InputQueue sau (hiện tại không phá build).
		CI: Mọi PR/commit lên main sẽ build/test với JDK 17 tự động (Ubuntu runners).
		Fat-jar & scripts: Tạo file server-all.jar để chạy server nhanh bằng .bat/.sh. Không ảnh hưởng IDE hay lệnh Gradle thường.

## 1.0.22 

	Sửa FPS = 0 (đo FPS theo lần vẽ thực tế)
	
## 1.0.22 fix 13/09/2025

	Chỉnh lại cấu trúc game
		Tách class GameCanvas:
			rt.client.game.ui.GameCanvas.java (đã cập nhật) – trở nên “mỏng”, chỉ điều phối các renderer ở trên.
			rt.client.game.ui.hud.HudRenderer.java (mới) – vẽ HUD chữ (FPS/Ping) góc trái.
			rt.client.game.ui.render.GridRenderer.java (mới) - vẽ lưới (có cache kích thước).
			rt.client.game.ui.render.EntityRenderer.java (mới) – vẽ entity (chấm + nhãn).
			rt.client.game.ui.tile.TileRenderer.java (mới) – vẽ tile map (ô solid).
		Tách class ClientApp:
			rt.client.input.InputState – giữ trạng thái WASD/Arrow, không còn lồng trong ClientApp.
			rt.client.app.AdminHotkeys – xử lý F1 (listSessions), F2 (teleport you 5 5), F3 (reloadMap), F4 (toggle HUD).
			rt.client.ui.RenderLoop – vòng lặp 60 FPS, gọi repaint() an toàn.
			rt.client.app.ClientApp – gọn lại, chỉ khởi tạo và “lắp ghép” các phần.

## 1.0.23
	
	Sửa lỗi người chơi hiển thị sau khi thoát khỏi máy khách

## 1.0.24
 
1) Mục tiêu
• Thêm nền tảng sinh thế giới dạng CHUNK (deterministic theo seed) mà không phá vỡ luồng map tĩnh hiện có.
• Stream chunk theo yêu cầu từ client (C2S: chunk_req → S2C: chunk).
• Giữ nguyên World/map tĩnh để server vẫn chạy an toàn; collision theo chunk sẽ bật ở giai đoạn sau.
2) Phạm vi (Giai đoạn 1 trong kế hoạch)
• Biome cơ bản: Ocean / Plain / Forest / Desert + điểm Mountain có collision (BitSet).
• Chưa bật collision trong World.step (sẽ thực hiện ở Giai đoạn 2).
• Không đổi cấu trúc Session/Config, không xóa hệ map tĩnh hiện có.
3) Các file mới
common/src/main/java/rt/common/world/ChunkPos.java – định nghĩa kích thước chunk và toạ độ (cx,cy).
common/src/main/java/rt/common/world/ChunkData.java – dữ liệu 1 chunk: layer1, layer2, collision BitSet.
common/src/main/java/rt/common/world/WorldGenConfig.java – tham số seed và tỉ lệ biome.
common/src/main/java/rt/common/world/WorldGenerator.java – generate(cx,cy) sinh chunk từ seed.
common/src/main/java/rt/common/net/dto/SeedS2C.java – S2C: bật chế độ chunk ở client (seed, chunkSize, tileSize).
common/src/main/java/rt/common/net/dto/ChunkReqC2S.java – C2S: yêu cầu một chunk.
common/src/main/java/rt/common/net/dto/ChunkS2C.java – S2C: trả dữ liệu chunk.
server/src/main/java/rt/server/world/chunk/ChunkService.java – cache + truy xuất chunk từ WorldGenerator.
4) Cập nhật WsTextHandler
• Thêm field ChunkService;
• constructor: khởi tạo WorldGenerator(seed) và ChunkService (seed tạm thời là hằng cố).
• case "hello": gửi SeedS2C(seed, ChunkPos.SIZE, tileSize=32).
• case "chunk_req": gọi chunkService.get(cx,cy) và gửi ChunkS2C.
5) Luồng chạy mới (E2E)
1. Client kết nối và gửi "hello".
2. Server trả HelloS2C, rồi SeedS2C(seed, chunkSize=64, tileSize=32).
3. Client chuyển sang chế độ chunk và gửi ChunkReqC2S cho các chunk cần hiển thị.
4. Server lấy dữ liệu từ ChunkService và gửi ChunkS2C.
5. Client vẽ tile theo layer1 (palette đơn giản) và cache để cuộn mượt khi di chuyển.
6) Phần chưa thay đổi (cố ý)
• World.java chưa bật collision theo chunk, để tránh rủi ro sớm.
• Hệ map tĩnh/MapS2C chưa xóa để tương thích ngược, có thể tắt khi POC chunk ổn.
7) Kiểm thử nhanh
• Client nhận được SeedS2C sau khi hello.
• Gửi {"type":"chunk_req","cx":0,"cy":0} → nhận "chunk" với layer/collision.
• Vẽ 3×3 chunk quanh nhân vật/camera; tự động xin chunk khi sang chunk mới.
8) DoD – Giai đoạn 1
• Stream được các chunk khác nhau theo cx,cy với cùng seed cho kết quả lặp lại.
• Không crash khi yêu cầu nhiều chunk; cache hoạt động.

thế giới được sinh theo chunk từ seed

## 1.0.25

	Xóa map cũ tránh lẫn lộn
	fix va chạm

## 1.0.26

	Thêm map lớn biển và lục địa	
	fix lỗi render

## 1.0.27

	fix nhân vật khi di chuyển bị giật(khi set màn hình đi theo nhân vật)

## 1.0.28

	spawn tại lục địa
	fix nhân vật giật giật khi di chuyển (tốn hiệu CPU hơn)
	
## 1.0.29

	Mục tiêu
		Chuẩn hoá “địa hình” + ID/Name (server/common)
		Có enum địa hình (Ocean, Plain, Forest, Desert, Mountain…) kèm id, name, blocked.
		Toàn bộ generator và client render dùng đúng ID này → dễ debug, log, HUD.
		Quản lý LỤC ĐỊA (continent) có ID/Name riêng (server)
		Lọc “đất/biển” bằng mặt nạ continental (đã có), nhưng bổ sung labeling: lục địa = thành phần liên thông của “đất”.
		Dò/labeled lười (lazy) theo “macro-grid” (ô lớn, ví dụ 128–256 tile/ô), chỉ tính nơi có người chơi đi qua.
		Mỗi lục địa có continentId (ổn định theo seed) + tên (deterministic từ seed) + bbox, tâm, diện tích ước lượng.
		Admin lệnh: cont here, cont list, cont goto <id> → test rất nhanh.
		Biomes cấp 3 theo đúng tỷ lệ trong kế hoạch (client giữ màu, server sinh)
		Thay vì ngưỡng noise cố định, mỗi lục địa có ngưỡng riêng để đạt mục tiêu: Đồng bằng ~40–60%, Rừng ~30–50%, Sa mạc ~10–20%.
		Cách đơn giản: dùng 1 noise, nhưng thay ngưỡng theo percent đặt cho lục địa; tính percent bằng sampling nhanh trên macro-grid của lục địa đó (deterministic theo seed).
		Thiện tại Plain/Forest/Desert đang là placeholder theo noise (đúng). Sang bước 3, đưa chúng về đúng tỷ lệ như bảng kế hoạch.
	
	Enum địa hình (ID/Name/blocked) + rt/common/world/Terrain.java
	Bổ sung tham số “scale/tỷ lệ” trong config generator + rt/common/world/WorldGenConfig.java
	Generator dùng “macro-scale” + rt/common/world/WorldGenerator.generate(...)
	ContinentIndex (label lục địa + admin lệnh) + rt/server/world/geo/ContinentIndex.java
		Tạo một instance ContinentIndex trong MainServer cùng với WorldGenerator (tiêm vào ChunkService nếu muốn dùng trong GUI/dev), hoặc để trong WsTextHandler như một service dùng chung (không tạo mỗi connection).
		Admin lệnh (trong WsTextHandler):
		cont here → lấy pos của player, truy vấn idAtTile, trả id + name.	
		cont list → liệt kê vài meta đã có.
		cont goto <id> → tìm tâm gần anchor (ví dụ anchorcell + cell8,8) rồi world.teleport.
		Đây là skeleton đủ để đặt tên & ID cho lục địa, test nhanh, và sẽ mở rộng dần.
	Quy ước ID & tên (để bạn dùng luôn)
		Terrain IDs (ổn định):
		0: Biển (blocked)
		2: Đồng bằng
		3: Rừng rậm
		4: Sa mạc
		5: Núi (blocked)
		Continent ID: số nguyên dương ổn định theo seed + anchor (không đụng nhau xác suất cao).
		Tên: sinh từ seed+ID (deterministic), ví dụ “Aron”, “Calen”… có thể thay bảng âm tiết tiếng Việt sau.

## 1.0.30 
	
	Thêm lớp chạy độc lập: ASCII preview + thống kê + hash kiểm định - common/src/main/java/rt/tools/MapAsciiPreview.java
	Test JUnit khẳng định determinism theo seed - common/src/test/java/rt/common/world/WorldGeneratorDeterminismTest.java
	Tiện chạy nhanh từ Gradle (không cần thêm plugin Application) - edit common/build.gradle.kts
	Cách chạy
		Test determinism: gradlew :common:test --tests rt.common.world.WorldGeneratorDeterminismTest
			BUILD SUCCESSFUL in 9s
			3 actionable tasks: 2 executed, 1 up-to-date
		Xem ASCII map + thống kê + hash: gradlew :common:runPreview -Pseed=123456789
			seed=123456789, area=16200 tiles
			OCEAN    75.00%
			PLAIN    25.00%
			FOREST    0.00%
			DESERT    0.00%
			MOUNTAIN  0.00%
			hash=dc308be83f25082702341fcef44b51027c9936b4bccc8ff0d29dcaaf4d9080c4

## 1.0.31

	World Map (fullscreen) skeleton + panning + xem tọa độ (6) — render từ seed (client-only), nhấn M để mở/đóng.
		NetClient – sửa case "seed" + bỏ trùng onArrive
		GameCanvas – thêm mini-map (HUD vẫn gọi trong Canvas)
		ClientApp – đăng ký seed callback + phím M
		Palette trong MapRenderer

## 1.0.32

### Bug

	lag do mini-map render toàn bộ ảnh mỗi frame trên EDT và mỗi pixel lại generate() cả chunk.
	
### Cách fix

	Giảm vùng phủ mini-map (zoom hợp lý).
	Render theo chunk có cache (mỗi chunk chỉ sinh 1 lần/khung).
	Không render trên EDT mỗi frame → render nền (background) có “throttle”, khung vẽ chỉ hiển thị ảnh cache.

### vào việc

	A) MapRenderer – cache chunk + palette nhanh
	B) MiniMapRenderer – render nền + throttle (không block EDT)
		Lưu ý: giảm tilesPerPixel (4–8) giúp mini-map chỉ hiển thị khu vực quanh người chơi → rất nhẹ. Bản đồ toàn màn hình (phím M) sẽ lo việc pan xa.
	C) GameCanvas – gọi mini-map đúng cách (không render trong EDT)
	D) ClientApp – giữ callback seed như trước

### Kết quả mong đợi

	FPS trở lại ~60 vì EDT không còn generate map; mini-map cập nhật ~5 lần/giây tối đa, mượt đủ dùng.
	CPU không spike khi mở game.
	Nếu còn tụt FPS khi mở bản đồ toàn màn hình (phím M), mình sẽ áp dụng cùng chiến lược: render nền + cache + pan theo block.
	
#### Xuất hiện thêm
	xuất hiện bug IllegalArgumentException
		Sửa nhanh (2 chỗ)
			WorldMapScreen — đừng refresh() khi chưa có kích thước
				Bỏ gọi mapPanel.refresh() trực tiếp trong constructor.
				Gọi sau khi layout xong bằng SwingUtilities.invokeLater(...).
				refresh() tự bỏ qua khi w/h <= 0.
			Bảo hiểm trong MapRenderer.render(...) + if (w <= 0 || h <= 0) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Nhấn M tụt FPS khi mở map full - áp dụng cơ chế render nền + cache cho World Map giống mini-map để mượt hẳn.

## 1.0.33

	fix "Nhấn M tụt FPS khi mở map full"
	2 vấn đề:
		Mở map làm đơ vì WorldMapScreen đang render full ảnh trên EDT + scale quá lớn (32 tiles/pixel ⇒ phải generate rất nhiều chunk).
		Bấm M lúc được lúc không vì game vẫn gửi input khi map mở → nhân vật di chuyển, chunk tiếp tục tải.
	Sửa nhanh:
		A) WorldMapScreen: render nền + giảm scale
			Đổi zoom mặc định xuống 2.0 tiles/pixel.
			Render ảnh off-EDT (background), không chặn UI.
		B) Trong ClientApp: 
			Chặn input khi map đang mở (không cho nhân vật chạy, không “kéo” chunk)
			Hotkey M mở/đóng và set cờ:
			Key listener: bỏ qua khi map mở.
			Gửi input định kỳ: khi map mở, gửi all-false (không di chuyển).
			
	Lưu ý nhấn M viết hoa để mở bản đồ
	
## 1.0.34

	Mở bản đồ ngay trong khung game bằng một overlay đặt trên JLayeredPane (không fullscreen, không đơ EDT).
		Mini-map: 1 pixel bản đồ ≈ 6 tiles (hằng MINI_TPP = 6.0).
		Bản đồ lớn (overlay): 1 pixel bản đồ ≈ 2 tiles (hằng BIG_TPP = 2.0).
		
	Công thức chuyển đổi:
		Từ pixel bản đồ → tile thế giới: gx = originX + floor(px * tilesPerPixel), gy = originY + floor(py * tilesPerPixel).
		Từ tile thế giới → pixel bản đồ: px = (gx - originX)/tilesPerPixel, py = (gy - originY)/tilesPerPixel.
		(Trong thế giới, 1 tile ≈ tileSize px hiển thị, vd 32px; mini/large map làm việc trực tiếp trên tiles nên không phụ thuộc độ phân giải.)
	
	Làm map lớn trong khung game (overlay)
		1) Tạo overlay (render nền, rất nhẹ) - rt/client/game/ui/map/WorldMapOverlay.java
		2) Gắn overlay vào ClientApp (thay vì JDialog)
			Hotkey M
			(Option) Pan bản đồ lớn bằng phím mũi tên khi overlay mở:
			
	Tóm tắt tỉ lệ (có thể đổi ở hằng số):
		Mini-map: tilesPerPixel = 6.0 → hiển thị vùng quanh người chơi rộng ≈ (width*6) × (height*6) tiles.
		Map lớn (overlay): tilesPerPixel = 2.0 → chi tiết hơn 3× so với mini-map.
		Có thể cho zoom bậc thang: [8.0, 4.0, 2.0, 1.0] (mỗi nấc nhân đôi chi tiết).
	
## 1.0.35
	
	Chỉnh sửa lại bố cục
	
## 1.0.36

	Kế hoạch fix (đợt 1)
		Menu chuột phải chỉ xuất hiện trên vùng bản đồ (không hiện ngoài game).
		Bố cục overlay: mép bản đồ cách mép khung game trên/trái/phải 2 ô, dưới 3 ô; panel điều hướng không trong suốt; nền bản đồ tối; thêm nút Reload.
		Đồng bộ màu giữa thế giới và bản đồ (dùng chung một bảng màu).
		Sửa kẹt input sau khi đóng bản đồ (trả focus + clear phím).
		Hiển thị tỉ lệ (Mini-map: góc phải trên; Bản đồ lớn: trên cùng panel điều hướng).

	Kế hoạch fix (đợt 2)
		Popup chỉ trong khu vực bản đồ.
		Bản đồ cách mép theo ô (trên/trái/phải: 2 ô; dưới: 3 ô).
		Panel điều hướng đặc, nền bản đồ tối, có Reload.
		Màu bản đồ khớp thế giới (dùng chung TerrainPalette). - rt/common/world/TerrainPalette.java + rt/client/world/ChunkCache
		Đóng bản đồ xong không kẹt phím, focus quay lại game.
		Hiện tỉ lệ: mini-map (góc phải trên), overlay (trên cùng panel trái). - WorldMapOverlay
		
	Kế hoạch fix (đợt 3)
		rt/client/world/ChunkCache.java
			Khớp màu với bản đồ: dùng TerrainPalette.color(id) cho ảnh của chunk (thế giới).
			Không xài bảng màu cứng nữa.
		rt/client/app/ClientApp.java - fix mới vào game nhân vật cũng không di chuyển được
			nguyên nhân “mới vào game không di chuyển” là do KeyListener gắn vào JFrame dễ bị hụt focus; mình chuyển sang Key Bindings (InputMap/ActionMap) ở RootPane để nhận phím ngay khi cửa sổ có focus, và chỉ còn một nơi cập nhật InputState. Đồng thời vẫn giữ pan bằng phím mũi tên khi map mở.
			Điểm mấu chốt:
				Movement dùng Key Bindings ở RootPane nên ngay khi cửa sổ có focus là nhận phím (không lệ thuộc component con).
				Không còn 2 KeyListener cho movement.
				Khi tắt map hoặc mất focus → reset input + focus lại GameCanvas






























