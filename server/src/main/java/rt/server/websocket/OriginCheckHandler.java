package rt.server.websocket;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;

public class OriginCheckHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final Set<String> allowedOrigins;

    public OriginCheckHandler(Set<String> allowedOrigins) {
        super(false); // không auto-release vì ta sẽ forward request xuống WebSocket handler
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Chỉ kiểm tra nếu header Origin tồn tại (trình duyệt). Native client (OkHttp) có thể không gửi.
        String origin = req.headers().get(ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            byte[] body = ("Forbidden origin: " + origin).getBytes(StandardCharsets.UTF_8);
            FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, FORBIDDEN);
            res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
            res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
            res.content().writeBytes(body);
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        // OK: chuyển tiếp xuống pipeline để WebSocketServerProtocolHandler xử lý upgrade
        ctx.fireChannelRead(req.retain());
    }
}
