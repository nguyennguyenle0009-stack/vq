package rt.common.game;

public final class Units {
    private Units(){}

    /** 1 tile = 32px (client sẽ dùng để vẽ) */
    public static final int TILE_PX = 32;

    /** Kích thước world theo Ô (logic server) */
    public static final int WORLD_W_TILES = 25; // ~ 800 / 32
    public static final int WORLD_H_TILES = 19; // ~ 600 / 32

    /** Tốc độ logic: ô/giây (giữ cảm giác ~120px/s => 120/32 = 3.75 tile/s) */
    public static final double SPEED_TILES_PER_SEC = 3.75;

    // Helper cho client khi cần quy đổi
    public static int toPx(double tiles){ return (int)Math.round(tiles * TILE_PX); }
}