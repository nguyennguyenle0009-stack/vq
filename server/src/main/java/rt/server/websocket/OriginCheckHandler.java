package rt.server.websocket;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import rt.common.net.ErrorCodes;
import rt.common.net.Jsons;
import rt.common.net.dto.ErrorS2C;

import java.util.Set;

public class OriginCheckHandler extends ChannelInboundHandlerAdapter {
    private final Set<String> allowed;
    public OriginCheckHandler(Set<String> allowed){ this.allowed = allowed; }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest req) {
            HttpHeaders h = req.headers();
            String origin = h.get("Origin");
            if (origin != null && !allowed.contains(origin)) {
                // Gửi lỗi JSON rồi đóng
                var err = new ErrorS2C(ErrorCodes.ORIGIN_FORBIDDEN, "Origin not allowed: " + origin);
                String json = Jsons.OM.writeValueAsString(err);
                ctx.writeAndFlush(new TextWebSocketFrame(json)).addListener(ChannelFutureListener.CLOSE);
                req.release();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}
