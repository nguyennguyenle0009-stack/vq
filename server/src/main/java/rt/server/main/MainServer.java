package rt.server.main;

import rt.server.config.ServerConfig;
import rt.server.game.loop.GameLoop;
import rt.server.game.loop.SnapshotBuffer;
import rt.server.game.loop.SnapshotStreamer;
import rt.server.input.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.world.World;

public class MainServer {
	  public static void main(String[] args) throws Exception {
	    var sessions = new SessionRegistry();
	    var inputs   = new InputQueue();
	    var snaps    = new SnapshotBuffer();
	    var world    = new World(sessions);

	    var ws = new WsServer(8080, sessions, inputs);
	    ws.start();
	    var cfg = ServerConfig.load();
	    org.slf4j.LoggerFactory.getLogger("rt.server").info("Starting with {}", cfg);

	    System.out.println("Server started at ws://localhost:8080/ws");

	    Thread loop = new Thread(new GameLoop(world, inputs, snaps, 60.0), "loop-60tps");
	    Thread stream = new Thread(new SnapshotStreamer(sessions, snaps, 20), "stream-12hz");
	    loop.start(); stream.start();

	    // Tắt êm khi IDE/Gradle bấm Stop
	    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	      System.out.println("Shutdown requested. Stopping server...");
	      ws.stop();
	    }));

	    Thread.currentThread().join();
	  }
	}