package rt.client.world;

import java.util.function.BiConsumer;

public final class ChunkStreamer {
    private final ChunkCache cache;
    private final BiConsumer<Integer,Integer> send; // send (cx,cy)

    public ChunkStreamer(ChunkCache cache, BiConsumer<Integer,Integer> send){
        this.cache = cache; this.send = send;
    }

    public void ensureAround(int centerCx,int centerCy){
        for(int dy=-ChunkCache.R; dy<=ChunkCache.R; dy++){
            for(int dx=-ChunkCache.R; dx<=ChunkCache.R; dx++){
                int cx = centerCx+dx, cy=centerCy+dy;
                if (cache.get(cx,cy)==null){ send.accept(cx,cy); }
            }
        }
    }
}
