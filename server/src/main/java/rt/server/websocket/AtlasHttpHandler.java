package rt.server.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.config.ServerConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/** Phục vụ tile atlas TMS qua HTTP (trước khi nâng cấp lên WebSocket). */
final class AtlasHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger log = LoggerFactory.getLogger(AtlasHttpHandler.class);

    private final Path atlasDir;
    private final byte[] metaBytes;
    private final boolean available;

    AtlasHttpHandler(ServerConfig cfg) {
        if (cfg.atlasDir == null || cfg.atlasDir.isBlank()) {
            atlasDir = null;
            metaBytes = null;
            available = false;
        } else {
            atlasDir = Path.of(cfg.atlasDir);
            byte[] meta = null;
            if (Files.exists(atlasDir.resolve("meta.json"))) {
                try {
                    meta = Files.readAllBytes(atlasDir.resolve("meta.json"));
                } catch (Exception e) {
                    log.warn("Failed to load atlas meta: {}", e.toString());
                }
            }
            metaBytes = meta;
            available = meta != null;
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            sendStatus(ctx, req, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        if (!req.method().equals(HttpMethod.GET)) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        String uri = req.uri();
        if (uri == null) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        if (!uri.startsWith("/atlas")) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        if (!available) {
            sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND);
            return;
        }

        try {
            if (uri.equals("/atlas/meta.json")) {
                writeBytes(ctx, HttpResponseStatus.OK, metaBytes, "application/json");
            } else {
                Path tile = resolveTile(uri);
                if (tile == null || !Files.exists(tile)) {
                    sendStatus(ctx, req, HttpResponseStatus.NOT_FOUND);
                } else {
                    byte[] bytes = Files.readAllBytes(tile);
                    writeBytes(ctx, HttpResponseStatus.OK, bytes, "image/png");
                }
            }
        } catch (Exception ex) {
            log.warn("Atlas handler error: {}", ex.toString());
            sendStatus(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private Path resolveTile(String uri) {
        String[] parts = uri.split("/");
        if (parts.length != 5) return null;
        String z = parts[2];
        String x = parts[3];
        String y = parts[4];
        if (!y.endsWith(".png")) return null;
        return atlasDir.resolve(z).resolve(x).resolve(y);
    }

    private void writeBytes(ChannelHandlerContext ctx, HttpResponseStatus status, byte[] bytes, String contentType) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        HttpUtil.setContentLength(resp, bytes.length);
        ctx.writeAndFlush(resp);
    }

    private void sendStatus(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
        HttpUtil.setContentLength(resp, 0);
        ctx.writeAndFlush(resp);
    }
}

