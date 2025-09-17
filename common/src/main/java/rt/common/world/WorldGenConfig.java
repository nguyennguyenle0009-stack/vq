package rt.common.world;

public final class WorldGenConfig {
    public final long seed;
    // Tweaked ratios: increase Plain/Forest; special sub-forests will be added later
    public final double plainRatio;    // 0..1 within continent
    public final double forestRatio;   // 0..1 within continent

    public WorldGenConfig(long seed, double plainRatio, double forestRatio) {
        this.seed = seed;
        this.plainRatio = plainRatio;     // e.g., 0.55
        this.forestRatio = forestRatio;   // e.g., 0.35
    }
}
