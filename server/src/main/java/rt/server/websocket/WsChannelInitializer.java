package rt.server.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateHandler;
import rt.common.world.WorldGenConfig;
import rt.server.config.ServerConfig;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.world.World;
import rt.server.world.chunk.ChunkService;
import rt.server.world.geo.ContinentIndex;
import rt.server.world.geo.SeaIndex;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class WsChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final SessionRegistry sessions;
    private final InputQueue inputs;
    private final World world;
    private final ServerConfig cfg;
    private final ChunkService chunkservice;
    private final ContinentIndex continents;
    private final SeaIndex seas;        
    private final WorldGenConfig cfgGen; 

    public WsChannelInitializer(SessionRegistry sessions, InputQueue inputs, ServerConfig cfg, World world, 
    		ChunkService chunkservice, ContinentIndex continents,
    		SeaIndex seas, WorldGenConfig cfgGen) {
        this.sessions = sessions;
        this.inputs = inputs;
        this.cfg = cfg;
        this.world = world;
        this.chunkservice = chunkservice;
        this.continents = continents;
        this.seas = seas; this.cfgGen = cfgGen; 
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        // HTTP handshake
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(8 * 1024)); // đủ cho handshake/headers

        // Kiểm tra Origin (nếu có header, web browser). Desktop client thường không có -> cho qua.
        if (cfg.checkOrigin) {
            p.addLast(new OriginCheckHandler(Set.copyOf(cfg.allowedOrigins)));
        }

        // WebSocket + limit frame
        WebSocketServerProtocolConfig wsCfg = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(cfg.wsPath)
                .allowExtensions(cfg.wsAllowExtensions)
                .maxFramePayloadLength(cfg.wsMaxFrameKB * 1024)
                .handshakeTimeoutMillis(10_000)
                .checkStartsWith(false)
                .build();
        p.addLast(new WebSocketServerProtocolHandler(wsCfg));

        // (tuỳ chọn) gộp continuation frames (vẫn bị giới hạn bởi maxFramePayloadLength)
        p.addLast(new WebSocketFrameAggregator(cfg.wsMaxFrameKB * 1024));

        // (tuỳ chọn) đóng kết nối nếu 45s không có inbound
        p.addLast(new IdleStateHandler(cfg.idleSeconds, 0, 0, TimeUnit.SECONDS));
        
        p.addLast(new WsExceptionHandler());

        // Handler nghiệp vụ
        p.addLast(new WsTextHandler(sessions, inputs, world, cfg, chunkservice, continents, seas, cfgGen));
    }
}
