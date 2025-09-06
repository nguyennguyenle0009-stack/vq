package rt.server.game.loop;

import rt.server.world.World;

public class SnapshotBuffer {
    private volatile World.Snapshot latest = new World.Snapshot(0, java.util.Map.of());
    public void write(World.Snapshot s){ latest = s; }
    World.Snapshot latest(){ return latest; }
}

