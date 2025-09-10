package rt.server.websocket;

import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsExceptionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(WsExceptionHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String msg = String.valueOf(cause.getMessage());
        String low = msg == null ? "" : msg.toLowerCase();

        // 1) Các lỗi I/O bình thường khi client đóng/đứt kết nối -> DEBUG, không stacktrace
        boolean benign =
                cause instanceof java.net.SocketException
             || cause instanceof java.nio.channels.ClosedChannelException
             || (cause instanceof java.io.IOException
                 && (low.contains("connection reset")
                     || low.contains("by peer")
                     || low.contains("forcibly closed")
                     || low.contains("broken pipe")
                     || low.contains("connection aborted")));

        if (benign) {
            if (log.isDebugEnabled()) {
                log.debug("client disconnected: ch={} msg={}", ctx.channel().id().asShortText(), msg);
            }
            ctx.close();
            return;
        }

        // 2) Khung quá lớn -> có thể trả lỗi chuẩn rồi đóng (nếu bạn đã dùng ErrorS2C/ErrorCodes)
        if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            ctx.close();
            return;
        }

        // 3) Frame WS hỏng -> đóng gọn
        if (cause instanceof io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException) {
            ctx.close();
            return;
        }

        // 4) Còn lại mới WARN + stacktrace
        log.warn("WS pipeline exception", cause);
        ctx.close();
    }

}
