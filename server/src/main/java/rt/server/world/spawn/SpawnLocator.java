package rt.server.world.spawn;

import rt.server.world.chunk.ChunkService;

import java.util.Objects;

/** Computes a safe default spawn position based on cached chunk data. */
public final class SpawnLocator {
    private static final int CHUNK_SIZE = rt.common.world.ChunkPos.SIZE;
    private static final int CONT_CELL_TILES = 12_000;
    private static final int MACRO_STEP_CHUNKS = Math.max(2, CONT_CELL_TILES / CHUNK_SIZE);
    private static final int FINE_STEP_CHUNKS = 8;
    private static final int MAX_MACRO_RINGS = 64;
    private static final int MAX_FINE_RADIUS = 2048;

    private volatile boolean ready;
    private volatile double spawnX = 3;
    private volatile double spawnY = 3;
    private ChunkService chunkService;

    public synchronized void initialize(ChunkService chunkService) {
        this.chunkService = Objects.requireNonNull(chunkService, "chunkService");
        this.ready = false;
        computeDefaultSpawn();
    }

    public double spawnX() {
        ensureComputed();
        return spawnX;
    }

    public double spawnY() {
        ensureComputed();
        return spawnY;
    }

    private void ensureComputed() {
        if (!ready) {
            computeDefaultSpawn();
        }
    }

    private void computeDefaultSpawn() {
        if (chunkService == null) {
            return;
        }

        for (int ring = 0; ring <= MAX_MACRO_RINGS; ring++) {
            int radius = ring * MACRO_STEP_CHUNKS;
            if (probeRing(radius, MACRO_STEP_CHUNKS)) {
                ready = true;
                return;
            }
        }

        for (int radius = FINE_STEP_CHUNKS; radius <= MAX_FINE_RADIUS; radius += FINE_STEP_CHUNKS) {
            if (probeRing(radius, FINE_STEP_CHUNKS)) {
                ready = true;
                return;
            }
        }

        ready = true;
    }

    private boolean probeRing(int radius, int step) {
        if (radius == 0) {
            return scanChunkForFree(0, 0);
        }
        for (int cy = -radius; cy <= radius; cy += step) {
            if (scanChunkForFree(-radius, cy)) return true;
            if (scanChunkForFree(radius, cy)) return true;
        }
        for (int cx = -radius + step; cx <= radius - step; cx += step) {
            if (scanChunkForFree(cx, -radius)) return true;
            if (scanChunkForFree(cx, radius)) return true;
        }
        return false;
    }

    private boolean scanChunkForFree(int cx, int cy) {
        var chunk = chunkService.get(cx, cy);
        int size = chunk.size;
        for (int ty = 0; ty < size; ty++) {
            for (int tx = 0; tx < size; tx++) {
                int idx = ty * size + tx;
                if (!chunk.collision.get(idx)) {
                    spawnX = cx * (double) size + tx + 0.5;
                    spawnY = cy * (double) size + ty + 0.5;
                    return true;
                }
            }
        }
        return false;
    }
}
