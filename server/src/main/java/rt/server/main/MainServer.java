package rt.server.main;

import rt.common.util.DesktopDir;
import rt.common.util.LogDirs;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import rt.server.config.ServerConfig;
import rt.server.game.input.InputQueue;
import rt.server.game.loop.GameLoop;
import rt.server.game.loop.SnapshotStreamer;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.world.CompatWorlds;
import rt.server.world.World;
import rt.server.world.WorldRegistry;

public class MainServer {
	public static void main(String[] args) throws Exception {
		
		WorldRegistry worldReg = CompatWorlds.initFromClasspathConfig();
	    var sessions = new SessionRegistry();
	    var inputs   = new InputQueue();
	    var world    = new World(sessions, worldReg);
	    var cfg = ServerConfig.load();
	    var ws = new WsServer(cfg, sessions, inputs, world);
	    
	    //Log
	    var base = DesktopDir.resolve().resolve("Vương quyền").resolve("server");
	    Files.createDirectories(base);
	    System.setProperty("VQ_LOG_DIR", base.toString());
	    System.setProperty("LOG_STAMP",
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")));
	    
	    ws.start();

	    org.slf4j.LoggerFactory.getLogger("rt.server").info("Starting with {}", cfg);

	    Thread loop   = new Thread(new GameLoop(world, inputs, cfg.tps), "loop-" + cfg.tps + "tps");
	    Thread stream = new Thread(new SnapshotStreamer(sessions, world, cfg.snapshotHz), "stream-" + cfg.snapshotHz + "hz");
	    loop.start(); stream.start();

	    // Tắt êm khi IDE/Gradle bấm Stop
	    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	      System.out.println("Shutdown requested. Stopping server...");
	      ws.stop();
	    }));

	    Thread.currentThread().join();
  	}
}