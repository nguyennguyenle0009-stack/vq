package rt.server.main;

import rt.server.game.world.GameLoop;
import rt.server.game.world.SnapshotBuffer;
import rt.server.game.world.SnapshotStreamer;
import rt.server.session.InputQueue;
import rt.server.session.SessionRegistry;
import rt.server.websocket.WsServer;
import rt.server.game.world.World;

public class MainServer {
    public static void main(String[] args) throws Exception {
        var sessions = new SessionRegistry();
        var inputs   = new InputQueue();
        var snaps    = new SnapshotBuffer();
        var world    = new World(sessions);

        // WS server
        var ws = new WsServer(8080, sessions, inputs);
        ws.start();
        System.out.println("Server started at ws://localhost:8080/ws");

        // game loop + streamer
        new Thread(new GameLoop(world, inputs, snaps, 60.0), "game-loop-60TPS").start();
        new Thread(new SnapshotStreamer(sessions, snaps, 12), "snapshot-12Hz").start();

        Thread.currentThread().join();
    }
}