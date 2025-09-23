package rt.server.main;

import rt.common.util.DesktopDir;
import rt.common.util.LogDirs;
import rt.common.world.WorldGenerator;
import rt.common.world.gen.WorldPipeline;

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
		

		var cfgGen = new rt.common.world.WorldGenConfig(
			    cfg.worldSeed != 0 ? cfg.worldSeed : 20250917L,
			    0.55, 0.35
		);
		var gen  = new rt.common.world.WorldGenerator(cfgGen);
		var svc  = new rt.server.world.chunk.ChunkService(gen);
		var cont = new rt.server.world.geo.ContinentIndex(cfgGen);   // <-- thêm dòng này
		var seas = new rt.server.world.geo.SeaIndex(cfgGen);    
                WorldGenerator.configure(cfgGen);
                WorldPipeline.createDefault(cfgGen);

                world.enableChunkMode(svc);
                world.prewarmSpawnArea(2);
                var ws = new rt.server.websocket.WsServer(
                            cfg, sessions, inputs, world, svc, cont, seas, cfgGen     // ★ NEW params
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