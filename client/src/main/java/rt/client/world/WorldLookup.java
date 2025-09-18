package rt.client.world;

import rt.common.world.LocationSummary;
import rt.common.world.WorldGenConfig;
import rt.common.world.WorldGenerator;

/**
 * Bộ tra cứu địa hình client-side: dùng chung thuật toán world-gen với server.
 */
public final class WorldLookup {
    private WorldGenerator generator;
    private WorldGenerator.Sampler sampler;

    public synchronized void configure(long seed, double plainRatio, double forestRatio) {
        WorldGenConfig cfg = new WorldGenConfig(seed, plainRatio, forestRatio);
        this.generator = new WorldGenerator(cfg);
        this.sampler = generator.sampler();
    }

    public synchronized boolean ready() {
        return sampler != null;
    }

    public synchronized LocationSummary describe(double x, double y) {
        if (sampler == null) return null;
        long gx = Math.round(Math.floor(x));
        long gy = Math.round(Math.floor(y));
        return sampler.describe(gx, gy);
    }

    public synchronized int baseId(long gx, long gy) {
        if (sampler == null) return rt.common.world.BiomeId.OCEAN;
        return sampler.baseId(gx, gy);
    }

    public synchronized WorldGenerator generator() {
        return generator;
    }
}
