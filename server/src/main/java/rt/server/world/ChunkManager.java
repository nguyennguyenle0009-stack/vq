package rt.server.world;

import rt.common.map.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkManager {
    private final long seed;
    private final TerrainGenerator generator;
    private final ConcurrentHashMap<Long, TileChunk> cache = new ConcurrentHashMap<>();

    public ChunkManager(long seed, TerrainGenerator generator){
        this.seed = seed; this.generator = generator;
    }

    private static long key(int cx,int cy){ return (((long)cx)<<32) ^ (cy & 0xffffffffL); }

    public TileChunk get(int cx,int cy){
        return cache.computeIfAbsent(key(cx,cy), k -> generator.generate(seed, cx, cy));
    }
}
