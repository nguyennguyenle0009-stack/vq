package rt.common.world;

public final class WorldGenConfig {
    public final long seed;

    // Targets (giữ để cân bằng tổng quan; sub-biome không dùng nữa)
    public final double targetPlainPct, targetForestPct, targetDesertPct;

    // Features
    public final double targetLakePct;      // dùng để tinh chỉnh nếu cần (tham khảo)
    public final double targetMountainPct;  // núi thường (tham khảo)

    // Scales / thresholds
    public final int continentScaleTiles;   // ~6000
    public final int biomeScaleTiles;       // ~800 (moisture macro)
    public final int mountainScaleTiles;    // ~400
    public final int lakeMacroTiles;        // ~256
    public final int regionScaleTiles;      // ★ vùng lớn cho clumping (mặc định 64)
    public final double landThreshold;      // ~0.35
    public final double mountainThreshold;  // ~0.83
    
 // + field
    public final int provinceScaleTiles;   // kích cỡ 1 tỉnh; 224→~50k ô, 256→~65k ô

    // default (client & server dùng ctor 3 tham số vẫn OK)
    public WorldGenConfig(long seed){
        this(seed, 39.5, 30.0, 6000, 800, 400, 256, /*province*/256, 0.35, 0.83);
    }

    // legacy 3 tham số (giữ nguyên chỗ gọi hiện tại)
    public WorldGenConfig(long seed, double plainRatio, double forestRatio){
        this(seed,
             Math.max(0, plainRatio) * 100.0,
             Math.max(0, forestRatio) * 100.0,
             6000, 800, 400,
             256, /*province*/256,
             0.35, 0.83);
    }

    // overload đầy đủ (nếu muốn chỉnh province theo ý)
    public WorldGenConfig(long seed,
                          double plainPct, double forestPct,
                          int continentScaleTiles, int biomeScaleTiles, int mountainScaleTiles,
                          int lakeMacroTiles, int provinceScaleTiles,
                          double landThreshold, double mountainThreshold){
        this.seed = seed;
        this.targetPlainPct = plainPct;
        this.targetForestPct = forestPct;
        this.targetDesertPct = Math.max(0.0, 100.0 - plainPct - forestPct);
        this.targetLakePct = 3.0; this.targetMountainPct = 14.0;
        this.continentScaleTiles = continentScaleTiles;
        this.biomeScaleTiles = biomeScaleTiles;
        this.mountainScaleTiles = mountainScaleTiles;
        this.lakeMacroTiles = lakeMacroTiles;
		this.regionScaleTiles = 224;
        this.provinceScaleTiles = Math.max(128, provinceScaleTiles); // đảm bảo ≥128
        this.landThreshold = landThreshold;
        this.mountainThreshold = mountainThreshold;
    }

//    // ---- Constructors (tương thích chỗ gọi hiện tại) ----
//    public WorldGenConfig(long seed) {
//        this(seed, 39.5, 30.0,
//             6000, 800, 400, 256, 64,
//             0.35, 0.83);
//    }
    
// // Legacy convenience for client: ratios in 0..1 (DESERT = 1 - plain - forest)
//    public WorldGenConfig(long seed, double plainRatio, double forestRatio) {
//        this(seed,
//             Math.max(0, plainRatio) * 100.0,
//             Math.max(0, forestRatio) * 100.0,
//             /* continentScaleTiles */ 6000,
//             /* biomeScaleTiles     */ 800,
//             /* mountainScaleTiles  */ 400,
//             /* lakeMacroTiles      */ 256,
//             /* regionScaleTiles    */ 64,
//             /* landThreshold       */ 0.35,
//             /* mountainThreshold   */ 0.83);
//    }


    // Back-compat: (seed, plainRatio, forestRatio, cont, bio, mtnScale, landTh, mtnTh)
    public WorldGenConfig(long seed, double plainRatio, double forestRatio,
                          int continentScaleTiles, int biomeScaleTiles, int mountainScaleTiles,
                          double landThreshold, double mountainThreshold) {
        this(seed,
             Math.max(0, plainRatio) * 100.0,
             Math.max(0, forestRatio) * 100.0,
             continentScaleTiles, biomeScaleTiles, mountainScaleTiles,
             256, 64,
             landThreshold, mountainThreshold);
    }

//    public WorldGenConfig(long seed,
//                          double plainPct, double forestPct,
//                          int cont, int bio, int mtnScale,
//                          int lakeMacro, int regionScale,
//                          double landTh, double mtnTh) {
//        this.seed = seed;
//        this.targetPlainPct  = plainPct;
//        this.targetForestPct = forestPct;
//        this.targetDesertPct = Math.max(0.0, 100.0 - plainPct - forestPct);
//
//        this.targetLakePct      = 3.0;
//        this.targetMountainPct  = 14.0;
//
//        this.continentScaleTiles = cont;
//        this.biomeScaleTiles     = bio;
//        this.mountainScaleTiles  = mtnScale;
//        this.lakeMacroTiles      = lakeMacro;
//        this.regionScaleTiles    = Math.max(32, regionScale);
//        this.landThreshold       = landTh;
//        this.mountainThreshold   = mtnTh;
//    }
}
