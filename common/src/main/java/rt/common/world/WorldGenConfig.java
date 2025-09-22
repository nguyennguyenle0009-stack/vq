package rt.common.world;

public final class WorldGenConfig {
    public final long seed;

    public final double targetPlainPct, targetForestPct, targetDesertPct;
    public final double targetLakePct;
    public final double targetMountainPct;

    public final int continentScaleTiles;
    public final int biomeScaleTiles;
    public final int mountainScaleTiles;
    public final int lakeMacroTiles;
    public final int regionScaleTiles;     // dùng trong WorldGenerator
    public final int provinceScaleTiles;   // alias để code nào dùng tên này cũng chạy
    public final double landThreshold;
    public final double mountainThreshold;

    // --- DEFAULT (seed) ---
    public WorldGenConfig(long seed) {
        this(seed, 39.5, 30.0,
             6000, 800, 400, 256, /*region*/256,
             0.35, /*mount*/0.86);
    }

    // --- LEGACY (seed, plainRatio, forestRatio) ---
    public WorldGenConfig(long seed, double plainRatio, double forestRatio) {
        this(seed,
             Math.max(0, plainRatio) * 100.0,
             Math.max(0, forestRatio) * 100.0,
             6000, 800, 400,
             256, /*region*/256,
             0.35, /*mount*/0.86);
    }

    // --- BACK-COMPAT (8 tham số) vẫn giữ nguyên để ai cần vẫn dùng ---
    public WorldGenConfig(long seed, double plainRatio, double forestRatio,
                          int continentScaleTiles, int biomeScaleTiles, int mountainScaleTiles,
                          double landThreshold, double mountainThreshold) {
        this(seed,
             Math.max(0, plainRatio) * 100.0,
             Math.max(0, forestRatio) * 100.0,
             continentScaleTiles, biomeScaleTiles, mountainScaleTiles,
             256, /*region*/256,
             landThreshold, mountainThreshold);
    }

    // --- FULL ---
    public WorldGenConfig(long seed,
                          double plainPct, double forestPct,
                          int cont, int bio, int mtnScale,
                          int lakeMacro, int regionScale,
                          double landTh, double mtnTh) {
        this.seed = seed;
        this.targetPlainPct  = plainPct;
        this.targetForestPct = forestPct;
        this.targetDesertPct = Math.max(0.0, 100.0 - plainPct - forestPct);

        this.targetLakePct      = 10.0;   // theo bảng mới
        this.targetMountainPct  = 10.0;

        this.continentScaleTiles = cont;
        this.biomeScaleTiles     = bio;
        this.mountainScaleTiles  = mtnScale;
        this.lakeMacroTiles      = lakeMacro;
        this.regionScaleTiles    = Math.max(32, regionScale);
        this.provinceScaleTiles  = this.regionScaleTiles; // alias
        this.landThreshold       = landTh;
        this.mountainThreshold   = mtnTh;
    }
}
