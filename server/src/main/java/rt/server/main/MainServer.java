package rt.server.main;

import rt.common.util.DesktopDir;
import rt.common.util.LogDirs;
import rt.common.world.WorldGenConfig;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import rt.server.config.ServerConfig;
import rt.server.game.input.InputQueue;
import rt.server.game.loop.GameLoop;
import rt.server.game.loop.SnapshotStreamer;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.world.World;

public class MainServer {
	public static void main(String[] args) throws Exception {
		
		var sessions = new SessionRegistry();
		var inputs   = new InputQueue();
		var world    = new World(sessions);
		var cfg      = ServerConfig.load();
		

//		var cfgGen = new rt.common.world.WorldGenConfig(
//		        cfg.worldSeed != 0 ? cfg.worldSeed : 20250917L,
//		        0.55, 0.35,        // plainRatio, forestRatio
//		        6000, 800, 400,    // continentScaleTiles, biomeScaleTiles, mountainScaleTiles
//		        0.35, 0.82         // landThreshold, mountainThreshold
//		);
		
		var cfgGen = new WorldGenConfig(
				cfg.worldSeed != 0 ? cfg.worldSeed : 20250917L,  0.60, 0.38,    // plain, forest (desert = phần còn lại)
			    6000, 800, 400,
			    256, /*province*/224, // ← 224 ≈ 50k; 256 ≈ 65k
			    0.35, 0.82
			);
		var gen  = new rt.common.world.WorldGenerator(cfgGen);
		var svc  = new rt.server.world.chunk.ChunkService(gen);
		var cont = new rt.server.world.geo.ContinentIndex(cfgGen); 
		var seas = new rt.server.world.geo.SeaIndex(cfgGen);    

		world.enableChunkMode(svc);
		var ws = new rt.server.websocket.WsServer(
			    cfg, sessions, inputs, world, svc, cont, seas, cfgGen
			);
	    
	    //Log
	    var base = DesktopDir.resolve().resolve("Vương quyền").resolve("server");
	    Files.createDirectories(base);
	    System.setProperty("VQ_LOG_DIR", base.toString());
	    System.setProperty("LOG_STAMP",
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")));
	    
	    org.slf4j.LoggerFactory.getLogger("rt.server").info("ChunkService {}", System.identityHashCode(svc));
	    
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