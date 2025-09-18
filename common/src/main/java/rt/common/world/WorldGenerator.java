package rt.common.world;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * World generator nhiều tầng theo kế hoạch "Vương Quyền".
 *
 * <p>Pipeline:</p>
 * <ol>
 *   <li>Sinh đại dương + lục địa (deterministic theo seed).</li>
 *   <li>Bên trong lục địa, chia biome cấp 3: đồng bằng, rừng, sa mạc, đồng bằng kỳ ảo.</li>
 *   <li>Các chi tiết cấp 4: hồ, sông, dãy núi, tiểu-biome rừng.</li>
 *   <li>Tầng mở rộng (tile2) cho làng: nhà, đường, ruộng.</li>
 * </ol>
 */
public final class WorldGenerator {
    private final WorldGenConfig cfg;

    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    // ====== Lục địa ======
    private static final int  CONT_CELL = 12_000;   // kích thước ô macro
    private static final int  R_MIN     = 310;      // bán kính tối thiểu (≈ diện tích 300k ô)
    private static final int  R_MAX     = 490;      // bán kính tối đa (≈ diện tích 750k ô)
    private static final int  R_SPAN    = R_MAX - R_MIN + 1;
    private static final int  JITTER    = 500;      // lệch tâm trong ô macro
    private static final long LAT_SCALE = 480_000L; // chuẩn hoá vĩ độ cho núi tuyết
    private static final double LAND_SHAPE_NOISE = 0.18; // bờ biển lượn tự nhiên

    // ====== Biome cấp 3 ======
    private static final int  BIOME_CELL        = 7_500;   // cell lớn cho đồng bằng/rừng/sa mạc
    private static final int  VARIANT_CELL      = 2_800;   // cell cho biến thể đồng bằng kỳ ảo
    private static final int  FOREST_SUB_CELL   = 2_200;   // cell cho tiểu-biome rừng

    // ====== Hồ nước ======
    private static final int  LAKE_CELL         = 1_400;
    private static final int  LAKE_MIN_RADIUS   = 25;      // đường kính 50
    private static final int  LAKE_MAX_RADIUS   = 100;     // đường kính 200
    private static final double LAKE_PROBABILITY = 0.14;   // xác suất 1 cell tạo hồ

    // ====== Sông ======
    private static final int    RIVER_CELL       = 7_200;
    private static final double RIVER_WIDTH_MIN  = 0.005;
    private static final double RIVER_WIDTH_MAX  = 0.012;

    // ====== Núi ======
    private static final double MOUNTAIN_THRESHOLD       = 0.79;
    private static final double MOUNTAIN_BLOCK_THRESHOLD = 0.89;

    // ====== Làng ======
    private static final int  VILLAGE_CELL      = 4_800;
    private static final int  VILLAGE_MIN_SIZE  = 12;      // chiều rộng/chiều dài tối thiểu (tile)
    private static final int  VILLAGE_MAX_SIZE  = 30;      // tối đa theo kế hoạch

    public ChunkData generate(int cx, int cy) {
        final int N = ChunkPos.SIZE;
        byte[] l1 = new byte[N * N];
        byte[] l2 = new byte[N * N];
        BitSet coll = new BitSet(N * N);

        long x0 = (long) cx * N;
        long y0 = (long) cy * N;

        Map<Long, Lake> lakeCache = new HashMap<>();
        Map<Long, Village> villageCache = new HashMap<>();

        for (int ty = 0; ty < N; ty++) {
            for (int tx = 0; tx < N; tx++) {
                long gx = x0 + tx;
                long gy = y0 + ty;

                TileResult tr = sampleTile(gx, gy, lakeCache, villageCache);
                int idx = ty * N + tx;
                l1[idx] = (byte) tr.baseId;
                l2[idx] = (byte) tr.overlayId;
                if (tr.blocked) coll.set(idx);
            }
        }

        return new ChunkData(cx, cy, N, l1, l2, coll);
    }

    private TileResult sampleTile(long gx, long gy,
                                  Map<Long, Lake> lakeCache,
                                  Map<Long, Village> villageCache) {
        Continent cont = continentAt(gx, gy);
        if (cont == null) {
            return new TileResult(BiomeId.OCEAN, OverlayId.NONE, true);
        }

        int base = primaryBiome(cont, gx, gy);
        boolean blocked = false;
        int overlay = OverlayId.NONE;

        Lake lake = lakeAt(cont, gx, gy, lakeCache, base);
        if (lake != null) {
            return new TileResult(BiomeId.LAKE, OverlayId.NONE, true);
        }

        MountainSample mountain = mountainAt(cont, gx, gy);
        if (mountain != null) {
            base = mountain.id;
            blocked = mountain.blocked;
        } else {
            if (isRiver(cont, gx, gy, base)) {
                return new TileResult(BiomeId.RIVER, OverlayId.NONE, true);
            }

            if (base == BiomeId.FOREST) {
                base = forestVariant(cont, gx, gy);
            }

            VillageCell vc = villageCell(cont, gx, gy, base, blocked, villageCache, lakeCache);
            if (vc != null) {
                base = BiomeId.VILLAGE;
                overlay = vc.overlayId();
                blocked = vc.blocked();
            }
        }

        return new TileResult(base, overlay, blocked);
    }

    // ===== Biome cấp 3 =====

    private int primaryBiome(Continent cont, long gx, long gy) {
        double inland = clamp01(cont.field);
        double climate = valueNoise(cfg.seed * 0x9E3779B97F4A7C15L, gx, gy, BIOME_CELL);
        double humidity = valueNoise(cfg.seed * 0xC2B2AE3DL, gx + cont.info.cx, gy - cont.info.cy, BIOME_CELL / 2);

        double plainBase = clamp(cfg.plainRatio, 0.35, 0.65);
        double forestBase = clamp(cfg.forestRatio, 0.30, 0.55);

        double plainRatio = clamp(plainBase + inland * 0.20 + (climate - 0.5) * 0.05, 0.35, 0.70);
        double forestRatio = clamp(forestBase + (0.5 - inland) * 0.25 + (humidity - 0.5) * 0.18, 0.30, 0.55);
        double desertRatio = clamp(1.0 - plainRatio - forestRatio, 0.05, 0.25);

        double sum = plainRatio + forestRatio + desertRatio;
        plainRatio /= sum;
        forestRatio /= sum;
        desertRatio = 1.0 - plainRatio - forestRatio;

        double region = valueNoise(cfg.seed * 0xD6E8FEBBL, gx, gy, BIOME_CELL);
        if (region < plainRatio) {
            double variant = valueNoise(cfg.seed * 0xA24BAED4L, gx, gy, VARIANT_CELL);
            double border = Math.abs(region - plainRatio * 0.5) / Math.max(plainRatio, 1e-6);
            double threshold = 0.82 - Math.min(0.20, border * 0.15);
            return variant > threshold ? BiomeId.PLAIN_WEIRD : BiomeId.PLAIN;
        }
        if (region < plainRatio + forestRatio) {
            return BiomeId.FOREST;
        }

        if (inland < 0.12) {
            return BiomeId.PLAIN; // sa mạc sát bờ → chuyển về đồng bằng
        }
        return BiomeId.DESERT;
    }

    private int forestVariant(Continent cont, long gx, long gy) {
        double n = valueNoise(cfg.seed * 0xE6A36B4DL, gx, gy, FOREST_SUB_CELL);
        if (n > 0.93) return BiomeId.FOREST_DARK;
        if (n > 0.88) return BiomeId.FOREST_WEIRD;
        if (n > 0.84) return BiomeId.FOREST_MAGIC; // giảm một nửa diện tích biến thể
        if (n > 0.80) return BiomeId.FOREST_FOG;   // (Fog nhỏ hơn Magic)
        return BiomeId.FOREST;
    }

    // ===== Hồ =====

    private Lake lakeAt(Continent cont, long gx, long gy,
                        Map<Long, Lake> lakeCache, int base) {
        if (base == BiomeId.DESERT) return null; // tránh sa mạc

        long cellX = floorDiv(gx, LAKE_CELL);
        long cellY = floorDiv(gy, LAKE_CELL);
        for (long j = cellY - 1; j <= cellY + 1; j++) {
            for (long i = cellX - 1; i <= cellX + 1; i++) {
                Lake lake = resolveLake(cont, i, j, lakeCache);
                if (lake != null && lake.contains(gx, gy)) return lake;
            }
        }
        return null;
    }

    private Lake resolveLake(Continent cont, long cellX, long cellY, Map<Long, Lake> cache) {
        long key = lakeKey(cont.info.id, cellX, cellY);
        Lake cached = cache.get(key);
        if (cached != null) return cached.exists ? cached : null;

        long baseSeed = mix(cfg.seed * 0x8E8F2B9DL, cont.info.id ^ cellX, Long.rotateLeft(cellY, 11));
        if (to01(baseSeed) > LAKE_PROBABILITY) {
            cache.put(key, Lake.NONE);
            return null;
        }

        int radius = LAKE_MIN_RADIUS + (int) (to01(mix(baseSeed, 0x9E3779B97F4A7C15L, 0x165667B1L))
                * (LAKE_MAX_RADIUS - LAKE_MIN_RADIUS));
        int offsetMaxX = Math.max(1, LAKE_CELL / 2 - radius - 4);
        int offsetMaxY = Math.max(1, LAKE_CELL / 2 - radius - 4);
        int ox = (int) ((to01(mix(baseSeed, 0xBF58476D1CE4E5B9L, 0x94D049BB133111EBL)) - 0.5) * 2 * offsetMaxX);
        int oy = (int) ((to01(mix(baseSeed, 0x94D049BB133111EBL, 0xBF58476D1CE4E5B9L)) - 0.5) * 2 * offsetMaxY);
        long cx = cellX * LAKE_CELL + LAKE_CELL / 2 + ox;
        long cy = cellY * LAKE_CELL + LAKE_CELL / 2 + oy;

        Continent actual = continentAt(cx, cy);
        if (actual == null || actual.info.id != cont.info.id) {
            cache.put(key, Lake.NONE);
            return null;
        }
        int centerBiome = primaryBiome(actual, cx, cy);
        if (centerBiome == BiomeId.DESERT) {
            cache.put(key, Lake.NONE);
            return null;
        }

        Lake lake = new Lake(true, cont.info.id, cx, cy, radius);
        cache.put(key, lake);
        return lake;
    }

    // ===== Sông =====

    private boolean isRiver(Continent cont, long gx, long gy, int currentBiome) {
        if (currentBiome == BiomeId.DESERT || currentBiome == BiomeId.LAKE || currentBiome == BiomeId.RIVER)
            return false;

        double base = valueNoise(cfg.seed * 0x7F4A7C15L, gx, gy, RIVER_CELL);
        double closeness = Math.abs(base - 0.5);
        double width = lerp(RIVER_WIDTH_MIN, RIVER_WIDTH_MAX, clamp01(0.6 - cont.field));
        if (closeness > width) return false;

        // Ưu tiên vùng có núi/sườn hoặc gần bờ biển
        double ridge = ridgeNoise(cfg.seed * 0xD1B54A32D192ED03L, gx * 0xBF58476D1CE4E5B9L, gy * 0x94D049BB133111EBL);
        if (ridge > 0.70 || cont.field < 0.18) return true;

        long h = mix(cfg.seed * 0xC1B3C6DL, gx, gy);
        return (h & 0xFFL) < 12; // nhánh phụ nhỏ
    }

    // ===== Núi =====

    private MountainSample mountainAt(Continent cont, long gx, long gy) {
        double ridge = ridgeNoise(cfg.seed * 0xD6E8FEBBL, gx * 0xBF58476D1CE4E5B9L, gy * 0x94D049BB133111EBL);
        if (ridge < MOUNTAIN_THRESHOLD) return null;

        double lat = Math.max(cont.info.latNorm, Math.min(1.0, Math.abs((double) gy) / LAT_SCALE));
        double type = noise(cfg.seed * 0xC2B2AE3DL, gx * 0x165667B1L, gy * 0x9E3779B97F4A7C15L);

        int id;
        if (ridge > 0.96 || lat > 0.75) {
            id = BiomeId.MOUNTAIN_SNOW;
        } else if (type > 0.88) {
            id = BiomeId.MOUNTAIN_VOLCANO;
        } else if (type < 0.42) {
            id = BiomeId.MOUNTAIN_FOREST;
        } else {
            id = BiomeId.MOUNTAIN_ROCK;
        }

        boolean blocked = ridge > MOUNTAIN_BLOCK_THRESHOLD;
        if (!blocked && ridge > (MOUNTAIN_THRESHOLD + MOUNTAIN_BLOCK_THRESHOLD) * 0.5) {
            long h = mix(cfg.seed * 0x851B3E95L, gx, gy);
            blocked = (h & 3L) == 0L; // tạo chuỗi chắn xen kẽ khe đèo
        }

        return new MountainSample(id, blocked);
    }

    // ===== Làng =====

    private VillageCell villageCell(Continent cont, long gx, long gy, int base,
                                    boolean blocked,
                                    Map<Long, Village> villageCache,
                                    Map<Long, Lake> lakeCache) {
        if (blocked) return null;
        if (base != BiomeId.PLAIN && base != BiomeId.PLAIN_WEIRD) return null;

        long cellX = floorDiv(gx, VILLAGE_CELL);
        long cellY = floorDiv(gy, VILLAGE_CELL);
        for (long j = cellY - 1; j <= cellY + 1; j++) {
            for (long i = cellX - 1; i <= cellX + 1; i++) {
                Village village = resolveVillage(cont, i, j, villageCache, lakeCache);
                if (village != null && village.contains(gx, gy)) {
                    return village.sample(gx, gy);
                }
            }
        }
        return null;
    }

    private Village resolveVillage(Continent cont, long cellX, long cellY,
                                   Map<Long, Village> cache,
                                   Map<Long, Lake> lakeCache) {
        long key = villageKey(cont.info.id, cellX, cellY);
        Village cached = cache.get(key);
        if (cached != null) return cached.exists ? cached : null;

        long baseSeed = mix(cfg.seed * 0xB5297A4DL, cont.info.id ^ cellX, Long.rotateLeft(cellY, 7));
        if (to01(baseSeed) > 0.28) {
            cache.put(key, Village.NONE);
            return null;
        }

        int width = ensureOdd(VILLAGE_MIN_SIZE + (int) (to01(mix(baseSeed, 0x165667B1L, 0x9E3779B97F4A7C15L))
                * (VILLAGE_MAX_SIZE - VILLAGE_MIN_SIZE)));
        int height = ensureOdd(VILLAGE_MIN_SIZE + (int) (to01(mix(baseSeed, 0x9E3779B97F4A7C15L, 0x165667B1L))
                * (VILLAGE_MAX_SIZE - VILLAGE_MIN_SIZE)));
        int halfW = width / 2;
        int halfH = height / 2;

        int offsetMaxX = Math.max(1, VILLAGE_CELL / 2 - halfW - 4);
        int offsetMaxY = Math.max(1, VILLAGE_CELL / 2 - halfH - 4);
        int ox = (int) ((to01(mix(baseSeed, 0xBF58476D1CE4E5B9L, 0x94D049BB133111EBL)) - 0.5) * 2 * offsetMaxX);
        int oy = (int) ((to01(mix(baseSeed, 0x94D049BB133111EBL, 0xBF58476D1CE4E5B9L)) - 0.5) * 2 * offsetMaxY);
        long cx = cellX * VILLAGE_CELL + VILLAGE_CELL / 2 + ox;
        long cy = cellY * VILLAGE_CELL + VILLAGE_CELL / 2 + oy;

        Continent actual = continentAt(cx, cy);
        if (actual == null || actual.info.id != cont.info.id) {
            cache.put(key, Village.NONE);
            return null;
        }

        int baseBiome = primaryBiome(actual, cx, cy);
        if (baseBiome != BiomeId.PLAIN && baseBiome != BiomeId.PLAIN_WEIRD) {
            cache.put(key, Village.NONE);
            return null;
        }

        if (mountainAt(actual, cx, cy) != null) {
            cache.put(key, Village.NONE);
            return null;
        }
        if (isRiver(actual, cx, cy, baseBiome)) {
            cache.put(key, Village.NONE);
            return null;
        }

        if (!nearWaterForVillage(cx, cy, lakeCache)) {
            cache.put(key, Village.NONE);
            return null;
        }

        int spacing = 5 + (int) (to01(mix(baseSeed, 0xD6E8FEBBL, 0xC2B2AE3DL)) * 3); // 5..7 ô giữa nhà
        int roadHalf = 1;  // đường rộng 3 ô
        int houseHalf = 1; // nhà 3×3

        Village village = new Village(true, cont.info.id, cx, cy, halfW, halfH, spacing, roadHalf, houseHalf);
        cache.put(key, village);
        return village;
    }

    private boolean nearWaterForVillage(long cx, long cy, Map<Long, Lake> lakeCache) {
        final int radius = 128;
        final int step = 16;
        for (int dy = -radius; dy <= radius; dy += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                if (dx * dx + dy * dy > radius * radius) continue;
                long sx = cx + dx;
                long sy = cy + dy;
                if (isWaterTile(sx, sy, lakeCache)) return true;
            }
        }
        return false;
    }

    private boolean isWaterTile(long x, long y, Map<Long, Lake> lakeCache) {
        Continent c = continentAt(x, y);
        if (c == null) return true;
        int base = primaryBiome(c, x, y);
        if (lakeAt(c, x, y, lakeCache, base) != null) return true;
        return isRiver(c, x, y, base);
    }

    // ===== Continent field =====

    private Continent continentAt(long gx, long gy) {
        long mi = Math.floorDiv(gx, CONT_CELL);
        long mj = Math.floorDiv(gy, CONT_CELL);

        double best = -1e9;
        CInfo bestInfo = null;
        for (long j = mj - 1; j <= mj + 1; j++) {
            for (long i = mi - 1; i <= mi + 1; i++) {
                CInfo info = continentInfo(i, j);
                if (!info.exists) continue;

                long dx = gx - info.cx;
                long dy = gy - info.cy;
                double r = Math.hypot(dx, dy);

                double wobble = (noise(cfg.seed * 0xA24BAED4L,
                        (gx + dy) * 0x9FB21C651E98DF25L,
                        (gy - dx) * 0xC2B2AE3DL) - 0.5) * LAND_SHAPE_NOISE;
                double f = 1.0 - (r / info.R) + wobble;
                if (f > best) {
                    best = f;
                    bestInfo = info;
                }
            }
        }
        return (best > 0 && bestInfo != null) ? new Continent(bestInfo, best) : null;
    }

    private CInfo continentInfo(long mi, long mj) {
        long h = mix(cfg.seed * 0x9E3779B97F4A7C15L, mi, mj);
        boolean exists = to01(h) >= 0.70;
        if (!exists) {
            return new CInfo(false, 0, 0, 0, mi, mj, 0, 0.0);
        }

        long hx = mix(h, 0x1234ABCDL, 0xCAFEBABEL);
        long hy = mix(h, 0x9E3779B97F4A7C15L, 0xD1B54A32D192ED03L);
        int jx = (int) ((to01(hx) - 0.5) * 2 * JITTER);
        int jy = (int) ((to01(hy) - 0.5) * 2 * JITTER);

        long cx = mi * CONT_CELL + CONT_CELL / 2 + jx;
        long cy = mj * CONT_CELL + CONT_CELL / 2 + jy;
        int R = R_MIN + (int) (to01(mix(h, 0xA24BAED4L, 0x165667B1L)) * R_SPAN);
        long id = mix(h, 0xBF58476D1CE4E5B9L, 0x94D049BB133111EBL);
        double latNorm = clamp01(Math.abs((double) cy) / LAT_SCALE);

        return new CInfo(true, cx, cy, R, mi, mj, id, latNorm);
    }

    // ===== Helpers =====

    private static int ensureOdd(int v) { return (v & 1) == 0 ? v + 1 : v; }

    private static int floorDiv(long v, int d) { return (int) Math.floorDiv(v, d); }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    private static double clamp01(double v) { return clamp(v, 0.0, 1.0); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double smooth(double t) { return t * t * (3.0 - 2.0 * t); }

    private double valueNoise(long seed, long gx, long gy, int cell) {
        long ix = Math.floorDiv(gx, cell);
        long iy = Math.floorDiv(gy, cell);
        double fx = (double) (gx - ix * cell) / cell;
        double fy = (double) (gy - iy * cell) / cell;
        double sx = smooth(fx);
        double sy = smooth(fy);

        double v00 = to01(mix(seed, ix, iy));
        double v10 = to01(mix(seed, ix + 1, iy));
        double v01 = to01(mix(seed, ix, iy + 1));
        double v11 = to01(mix(seed, ix + 1, iy + 1));
        double a = lerp(v00, v10, sx);
        double b = lerp(v01, v11, sx);
        return lerp(a, b, sy);
    }

    private static double noise(long a, long b, long c) {
        return to01(mix(a, b, c));
    }

    private static double ridgeNoise(long a, long b, long c) {
        double n = noise(a, b, c);
        return 1.0 - 2.0 * Math.abs(n - 0.5);
    }

    private static double to01(long h) {
        return (h >>> 11) / (double) (1L << 53);
    }

    private static long mix(long a, long b, long c) {
        long x = a ^ Long.rotateLeft(b, 13) ^ Long.rotateLeft(c, 27);
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdl;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53l;
        x ^= (x >>> 33);
        return x;
    }

    private static long lakeKey(long id, long cx, long cy) {
        return (id * 0x9E3779B97F4A7C15L) ^ (cx << 32) ^ (cy & 0xffffffffL);
    }

    private static long villageKey(long id, long cx, long cy) {
        return (id * 0xC2B2AE3DL) ^ Long.rotateLeft(cx, 17) ^ Long.rotateLeft(cy, 5);
    }

    // ===== Data class =====

    private record TileResult(int baseId, int overlayId, boolean blocked) {}

    private record MountainSample(int id, boolean blocked) {}

    private record Continent(CInfo info, double field) {}

    private record CInfo(boolean exists, long cx, long cy, int R,
                         long cellX, long cellY, long id, double latNorm) {}

    private static final class Lake {
        static final Lake NONE = new Lake(false, 0, 0, 0, 0);

        final boolean exists;
        final long continentId;
        final long cx, cy;
        final int radius;

        Lake(boolean exists, long continentId, long cx, long cy, int radius) {
            this.exists = exists;
            this.continentId = continentId;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
        }

        boolean contains(long x, long y) {
            if (!exists) return false;
            long dx = x - cx;
            long dy = y - cy;
            return dx * dx + dy * dy <= (long) radius * radius;
        }
    }

    private static final class Village {
        static final Village NONE = new Village(false, 0, 0, 0, 0, 0, 0, 0, 0);

        final boolean exists;
        final long continentId;
        final long cx, cy;
        final int halfW, halfH;
        final int spacing;
        final int roadHalf;
        final int houseHalf;

        Village(boolean exists, long continentId, long cx, long cy,
                int halfW, int halfH, int spacing, int roadHalf, int houseHalf) {
            this.exists = exists;
            this.continentId = continentId;
            this.cx = cx;
            this.cy = cy;
            this.halfW = halfW;
            this.halfH = halfH;
            this.spacing = Math.max(4, spacing);
            this.roadHalf = Math.max(0, roadHalf);
            this.houseHalf = Math.max(1, houseHalf);
        }

        boolean contains(long x, long y) {
            if (!exists) return false;
            return Math.abs(x - cx) <= halfW && Math.abs(y - cy) <= halfH;
        }

        VillageCell sample(long x, long y) {
            int dx = (int) (x - cx);
            int dy = (int) (y - cy);
            if (Math.abs(dx) > halfW || Math.abs(dy) > halfH) return null;

            if (Math.abs(dx) <= roadHalf || Math.abs(dy) <= roadHalf) {
                return new VillageCell(OverlayId.ROAD, false);
            }

            int nearestX = Math.round(dx / (float) spacing) * spacing;
            int nearestY = Math.round(dy / (float) spacing) * spacing;
            if (Math.abs(dx - nearestX) <= houseHalf && Math.abs(dy - nearestY) <= houseHalf) {
                return new VillageCell(OverlayId.HOUSE, true);
            }
            return new VillageCell(OverlayId.FARM, false);
        }
    }

    private record VillageCell(int overlayId, boolean blocked) {}
}
