// rt/server/world/chunk/ChunkService.java
package rt.server.world.chunk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import rt.common.world.ChunkData;
import rt.common.world.ChunkPos;
import rt.common.world.WorldGenerator;
// ... các import khác

public final class ChunkService {
    private final WorldGenerator gen; // có cũng được, không dùng nữa
    private final ConcurrentMap<Long, ChunkData> cache = new ConcurrentHashMap<>();
    public ChunkService(WorldGenerator gen){ this.gen = gen; }

    public ChunkData get(int cx, int cy){
        long k = (((long)cx)<<32) ^ (cy & 0xffffffffL);
        return cache.computeIfAbsent(k, kk -> {
            final int N = ChunkPos.SIZE;
            byte[] l1 = new byte[N*N];
            byte[] l2 = new byte[N*N];
            java.util.BitSet coll = new java.util.BitSet(N*N);

            // ✅ gọi pipeline mới (static), KHÔNG dùng gen.generate(...)
            WorldGenerator.generateChunk(cx, cy, N, l1, l2, coll);

            return new ChunkData(cx, cy, N, l1, l2, coll);
        });
    }
}
