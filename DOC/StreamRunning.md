# StreamRunning — luồng chạy & trách nhiệm class

## Luồng/Thread

- **Netty boss/worker (WsServer)**  
  - Boss: accept kết nối WebSocket.  
  - Worker: I/O không chặn cho từng channel.
- **GameLoop** (mặc định 60 TPS)  
  - Chu kỳ cố định `dt = 1/TPS`.  
  - Mỗi tick:
    1) `InputQueue.drainAllTo(batch)`
    2) `World.applyInput(...)` cho từng InputEvent
    3) `World.step(dt)`
- **SnapshotStreamer** (mặc định 12 Hz)
  - Định kỳ build state: `World.copyForNetwork(ents)`
  - Gửi cho *tất cả* session: `Session.send(state)`

## Class chính & công dụng

- `rt.server.config.ServerConfig`  
  Nạp `application.properties` → `port`, `tps`, `snapshotHz`. Có default khi thiếu file.

- `rt.server.session.SessionRegistry`  
  - Map kênh/phiên: `attach(Session)`, `detach(Channel)`, `byChannel(Channel)`, `all()`.  
  - `Session` (inner class): giữ `playerId`, `x`, `y`, và `send(Object)` (serialize JSON và `writeAndFlush`).

- `rt.server.websocket.WsServer`  
  Khởi tạo Netty pipeline (`WsTextHandler`), expose `start()/stop()`.

- `rt.server.websocket.WsTextHandler`  
  - Khi `handlerAdded`: tạo `Session` mới, `sessions.attach`, trả `{"type":"hello","you":<id>}`.  
  - Khi nhận message:
    - `type="hello"`: log tên (nếu có), echo lại hello.
    - `type="input"`: `InputQueue.offer(playerId, seq, keys)` và trả `{"type":"ack","seq":...}`.

- `rt.server.game.input.InputQueue`  
  Hàng đợi thread-safe. Lọc gói **cũ/trùng** bằng `lastSeqSeen`.

- `rt.server.world.World`  
  Trạng thái server-authoritative; lưu vị trí người chơi, áp input → step vật lý, clamp biên.  
  `copyForNetwork(out)` điền `Map<String, {x,y}>`.

- `rt.server.game.loop.GameLoop`  
  Vòng lặp cố định; xử lý input + `World.step(dt)`.

- `rt.server.game.loop.SnapshotStreamer`  
  Gửi state đều cho mọi `Session` theo `snapshotHz`.

## Luồng dữ liệu

Client → Server:
