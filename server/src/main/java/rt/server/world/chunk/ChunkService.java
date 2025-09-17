package rt.server.world.chunk;
import java.util.concurrent.*; import rt.common.world.*;

public final class ChunkService {
    private final WorldGenerator gen;
    private final ConcurrentMap<Long, ChunkData> cache = new ConcurrentHashMap<>();
    public ChunkService(WorldGenerator gen){ this.gen = gen; }
    public ChunkData get(int cx,int cy){
        long k = (((long)cx)<<32) ^ (cy & 0xffffffffL);
        return cache.computeIfAbsent(k, kk -> gen.generate(cx, cy));
    }
}
