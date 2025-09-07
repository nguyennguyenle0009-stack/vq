package rt.server.config;

/**
 * Thông số cấu hình cơ bản cho server.
 * - {@code port}: cổng WebSocket mà server lắng nghe.
 * - {@code tps}: số vòng lặp xử lý game mỗi giây (tick per second).
 * - {@code snapshotHz}: tần số gửi trạng thái/snapshot tới client mỗi giây.
 *
 * Tách các giá trị này ra khỏi mã nguồn giúp thay đổi cấu hình dễ dàng
 * mà không phải sửa trực tiếp trong các lớp server.
 */
public record ServerConfig(int port, double tps, int snapshotHz) {
}
