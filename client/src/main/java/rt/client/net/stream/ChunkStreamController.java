package rt.client.net.stream;

import rt.client.model.WorldModel;
import rt.client.world.ChunkCache;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates chunk streaming state shared between renderers and the websocket client.
 */
public final class ChunkStreamController {
    private final ChunkCache chunkCache = new ChunkCache();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    private int chunkSize = rt.common.world.ChunkPos.SIZE;
    private int tileSize = 32;
    private int lastCenterCx = Integer.MIN_VALUE;
    private int lastCenterCy = Integer.MIN_VALUE;

    public ChunkCache cache() {
        return chunkCache;
    }

    public int tileSize() {
        return tileSize;
    }

    public boolean isReady() {
        return ready.get();
    }

    public void applySeed(WorldModel model, long seed, int chunkSize, int tileSize) {
        this.chunkSize = chunkSize;
        this.tileSize = tileSize;
        ready.set(true);

        chunkCache.clear();
        lastCenterCx = Integer.MIN_VALUE;
        lastCenterCy = Integer.MIN_VALUE;

        int[] center = computePlayerChunk(model);
        ensureAround(center[0], center[1]);
        lastCenterCx = center[0];
        lastCenterCy = center[1];
    }

    public void maybeRequestAround(WorldModel model) {
        if (!ready.get() || model.you() == null) {
            return;
        }
        int[] center = computePlayerChunk(model);
        int cx = center[0];
        int cy = center[1];
        if (cx == lastCenterCx && cy == lastCenterCy) {
            return;
        }
        ensureAround(cx, cy);
        lastCenterCx = cx;
        lastCenterCy = cy;
    }

    private int[] computePlayerChunk(WorldModel model) {
        int cx = 0;
        int cy = 0;
        if (model.you() != null) {
            int px = (int) Math.round(model.youX() * tileSize);
            int py = (int) Math.round(model.youY() * tileSize);
            int chunkWidthPx = chunkSize * tileSize;
            cx = Math.floorDiv(px, chunkWidthPx);
            cy = Math.floorDiv(py, chunkWidthPx);
        }
        return new int[]{cx, cy};
    }

    private void ensureAround(int cx, int cy) {
        for (int dy = -ChunkCache.R; dy <= ChunkCache.R; dy++) {
            for (int dx = -ChunkCache.R; dx <= ChunkCache.R; dx++) {
                chunkCache.getOrGenerateLocal(cx + dx, cy + dy);
            }
        }
    }
}
