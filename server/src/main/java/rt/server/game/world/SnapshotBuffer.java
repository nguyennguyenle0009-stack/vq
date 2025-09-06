package rt.server.game.world;

public class SnapshotBuffer {
    private volatile World.Snapshot latest = new World.Snapshot(0, java.util.Map.of());
    void write(World.Snapshot s){ latest = s; }
    World.Snapshot latest(){ return latest; }
}

