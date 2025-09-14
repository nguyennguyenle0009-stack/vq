package rt.server.world;
public final class NoopCollision implements CollisionProvider {
    @Override public boolean blocked(int tx, int ty) { return false; }
}
