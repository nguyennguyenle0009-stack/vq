package rt.server.world.chunk;

import rt.common.world.ChunkData;
import rt.common.world.WorldGenerator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ChunkService {
    private final WorldGenerator gen;
    private final ChunkStorage storage;
    private final ConcurrentMap<Long, ChunkData> cache = new ConcurrentHashMap<>();

    public ChunkService(WorldGenerator gen, ChunkStorage storage) {
        this.gen = gen;
        this.storage = storage;
    }

    private static long key(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }

    public ChunkData get(int cx, int cy) {
        long k = key(cx, cy);
        return cache.computeIfAbsent(k, kk -> {
            ChunkData data = gen.generate(cx, cy);
            if (storage != null) {
                ChunkDelta delta = storage.load(cx, cy);
                if (delta != null) delta.apply(data);
            }
            return data;
        });
    }

    public void applyDelta(int cx, int cy, ChunkDelta delta) {
        if (delta == null || delta.isEmpty()) return;
        long k = key(cx, cy);
        cache.compute(k, (kk, existing) -> {
            ChunkData data = existing != null ? existing : gen.generate(cx, cy);
            if (existing == null && storage != null) {
                ChunkDelta persisted = storage.load(cx, cy);
                if (persisted != null) persisted.apply(data);
            }
            delta.apply(data);
            if (storage != null) storage.update(cx, cy, delta);
            return data;
        });
    }
}
