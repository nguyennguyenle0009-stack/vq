package rt.server.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rt.server.config.ServerConfig;
import rt.server.game.loop.GameLoop;
import rt.server.game.loop.SnapshotBuffer;
import rt.server.game.loop.SnapshotStreamer;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.world.World;

/**
 * Điểm khởi động của server game.
 */
public class MainServer {
    private static final Logger log = LoggerFactory.getLogger(MainServer.class);

    public static void main(String[] args) throws Exception {
        // TODO: có thể đọc cấu hình từ file/CLI sau này
        var cfg = new ServerConfig(8080, 60.0, 20);

        var sessions = new SessionRegistry();
        var inputs   = new InputQueue();
        var snaps    = new SnapshotBuffer();
        var world    = new World(sessions);

        var ws = new WsServer(cfg.port(), sessions, inputs);
        ws.start();
        log.info("Server started at ws://localhost:{}/ws", cfg.port());

        Thread loop   = new Thread(new GameLoop(world, inputs, snaps, cfg.tps()),
                                   "loop-" + (int)cfg.tps() + "tps");
        Thread stream = new Thread(new SnapshotStreamer(sessions, snaps, cfg.snapshotHz()),
                                   "stream-" + cfg.snapshotHz() + "hz");
        loop.start();
        stream.start();

        // Tắt êm khi IDE/Gradle bấm Stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown requested. Stopping server...");
            ws.stop();
        }));

        Thread.currentThread().join();
    }
}
