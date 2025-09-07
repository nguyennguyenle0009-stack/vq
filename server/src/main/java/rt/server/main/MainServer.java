package rt.server.main;

import rt.server.config.ServerConfig;
import rt.server.game.loop.GameLoop;
import rt.server.game.loop.SnapshotStreamer;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.world.World;

public class MainServer {
	public static void main(String[] args) throws Exception {
	    var sessions = new SessionRegistry();
	    var inputs   = new InputQueue();
	    var world    = new World(sessions);
	    
	    var cfg = ServerConfig.load();
	    
	    var ws = new WsServer(cfg.port(), sessions, inputs);
	    ws.start();

	    org.slf4j.LoggerFactory.getLogger("rt.server").info("Starting with {}", cfg);
	    System.out.println("Server started at ws://localhost:" + cfg.port() +"/ws");

	    Thread loop   = new Thread(new GameLoop(world, inputs, cfg.tps()), "loop-" + cfg.tps() + "tps");
	    Thread stream = new Thread(new SnapshotStreamer(sessions, world, cfg.snapshotHz()), "stream-" + cfg.snapshotHz() + "hz");
	    loop.start(); stream.start();

	    // Tắt êm khi IDE/Gradle bấm Stop
	    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	      System.out.println("Shutdown requested. Stopping server...");
	      ws.stop();
	    }));

	    Thread.currentThread().join();
  	}
}