package rt.common.world;

public final class WorldGenConfig {
    public final long seed;
    public final double plainRatio, forestRatio;   // phần còn lại là desert
    public final int continentScaleTiles;          // ~ 6000 (điều chỉnh kích thước lục địa)
    public final int biomeScaleTiles;              // ~ 800
    public final int mountainScaleTiles;           // ~ 400
    public final double landThreshold;             // ~ 0.35
    public final double mountainThreshold;         // ~ 0.82

    public WorldGenConfig(long seed, double plainRatio, double forestRatio) {
        this(seed, plainRatio, forestRatio, 6000, 800, 400, 0.35, 0.82);
    }
    public WorldGenConfig(long seed, double pr, double fr,
                          int cont, int bio, int mtn,
                          double landTh, double mtnTh){
        this.seed=seed; this.plainRatio=pr; this.forestRatio=fr;
        this.continentScaleTiles=cont; this.biomeScaleTiles=bio; this.mountainScaleTiles=mtn;
        this.landThreshold=landTh; this.mountainThreshold=mtnTh;
    }
}
