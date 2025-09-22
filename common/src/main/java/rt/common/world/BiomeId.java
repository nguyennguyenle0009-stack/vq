package rt.common.world;

/** Numeric IDs used in TILE_INDEX=1. Keep OCEAN=0 for backward-compat,
 *  and use distinct ranges for new biomes. LAND is a classifier (not emitted).
 */
public final class BiomeId {
    public static final int OCEAN = 0;          // blocked
    public static final int LAND  = 1;          // classifier only

    // Core land biomes
    public static final int PLAIN        = 2;
    public static final int FOREST       = 3;
    public static final int DESERT       = 4;

    // Variants / features (still TILE_INDEX=1)
    public static final int PLAIN_WEIRD  = 6;

    public static final int LAKE         = 7;   // blocked
    public static final int RIVER        = 8;   // walkable (may be slow later)

    // Forest sub-biomes (walkable)
    public static final int F_FOG        = 20;
    public static final int F_MAGIC      = 21;
    public static final int F_WEIRD      = 22;
    public static final int F_DARK       = 23;

    // Mountains (blocked)
    public static final int M_SNOW       = 9;
    public static final int M_VOLCANO    = 10;
    public static final int M_FOREST     = 11;
    public static final int M_ROCK       = 12;

    private BiomeId() {}
}
