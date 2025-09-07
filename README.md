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
        
### fix bug 

	The import com.fasterxml cannot be resolved 
	JsonInclude cannot be resolved to a type 
	JsonInclude cannot be resolved to a variable
	
	=> rd /s /q server\build
	=> rd /s /q common\build
	
	Xoá tay build/ và bin/ (hoặc chạy ./gradlew clean).
	Eclipse: Project → Clean…
	Gradle → Refresh Project
	
### chạy server
	Xoá tay build/ và bin/ (hoặc chạy ./gradlew clean).
	Eclipse: Project → Clean…
	Gradle → Refresh Project
	
	./gradlew :server:run hoặc run as tại class MainServer
	
	# vq — Multiplayer 2D (WIP)

**Phiên bản này (Giai đoạn 2)** đã có:
- Hạ tầng Gradle multi-module: `server`, `client`, `common`
- WS server bằng Netty, handler text JSON
- Protocol tối giản: `hello`, `input`, `ack`, `state`
- Game loop 60 TPS + snapshot streamer 12 Hz
- Hàng đợi input thread-safe, lọc gói cũ/trùng
- Cấu hình tách rời (`application.properties`)
- Logback/SLF4J

---

## Yêu cầu hệ thống

- JDK 17/21 (khuyên 17).  
- Gradle wrapper đi kèm repo.

**Windows**: nếu thấy cảnh báo JDK 24 *native access*, có thể bỏ qua hoặc thêm vào `gradle.properties`: