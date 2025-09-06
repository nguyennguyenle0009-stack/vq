package rt.server;

/** Một gói input từ client (giữ trục chuyển động). */
public record InputEvent(String playerId, int seq, int ax, int ay) {}
