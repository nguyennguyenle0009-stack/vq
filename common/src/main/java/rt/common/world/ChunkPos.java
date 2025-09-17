package rt.common.world;

public record ChunkPos(int cx, int cy) {
    public static final int SIZE = 64; // 64×64 tiles
}
