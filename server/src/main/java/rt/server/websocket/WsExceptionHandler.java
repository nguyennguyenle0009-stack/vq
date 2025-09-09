package rt.server.websocket;

import io.netty.channel.*;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.common.net.ErrorCodes;
import rt.common.net.Jsons;
import rt.common.net.dto.ErrorS2C;

public class WsExceptionHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(WsExceptionHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String code = null, msg = cause.getMessage();
        if (cause instanceof TooLongFrameException) {
            code = ErrorCodes.PAYLOAD_TOO_LARGE;
        } else if (cause instanceof CorruptedWebSocketFrameException) {
            code = ErrorCodes.BAD_SCHEMA;
        }
        if (code != null) {
            try {
                String json = Jsons.OM.writeValueAsString(new ErrorS2C(code, msg != null? msg : code));
                ctx.writeAndFlush(new TextWebSocketFrame(json)).addListener(ChannelFutureListener.CLOSE);
            } catch (Exception e) {
                // ignore serialization error
                ctx.close();
            }
        } else {
            log.warn("WS pipeline exception", cause);
            ctx.close();
        }
    }
}
