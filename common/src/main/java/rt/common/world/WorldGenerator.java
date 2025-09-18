package rt.common.world;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * World generator nhiều tầng theo kế hoạch "Vương Quyền".
 *
 * <p>Pipeline:</p>
 * <ol>
 *   <li>Sinh đại dương + lục địa (deterministic theo seed).</li>
 *   <li>Bên trong lục địa, chia biome cấp 3 dạng cụm (đồng bằng, rừng, sa mạc).</li>
 *   <li>Các chi tiết cấp 4: hồ, sông, dãy núi, tiểu-biome rừng, làng.</li>
 *   <li>Tầng mở rộng (tile2) cho làng: nhà, đường, ruộng.</li>
 * </ol>
 */
public final class WorldGenerator {
    private final WorldGenConfig cfg;

    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    public WorldGenConfig config() { return cfg; }

    // ====== Lục địa ======
    private static final int  CONT_CELL = 12_000;   // kích thước ô macro
    private static final int  R_MIN     = 310;      // bán kính tối thiểu (≈ diện tích 300k ô)
    private static final int  R_MAX     = 490;      // bán kính tối đa (≈ diện tích 750k ô)
    private static final int  R_SPAN    = R_MAX - R_MIN + 1;
    private static final int  JITTER    = 500;      // lệch tâm trong ô macro
    private static final long LAT_SCALE = 480_000L; // chuẩn hoá vĩ độ cho núi tuyết
    private static final double LAND_SHAPE_NOISE = 0.18; // bờ biển lượn tự nhiên

    // ====== Biome cấp 3 dạng cụm ======
    private static final int  REGION_CELL       = 220;     // spacing giữa tâm cụm
    private static final int  REGION_R_MIN      = 140;     // bán kính cụm nhỏ nhất
    private static final int  REGION_R_MAX      = 230;     // bán kính cụm lớn nhất
    private static final double REGION_SHAPE_NOISE = 0.25; // méo cụm

    private static final int  VARIANT_CELL      = 180;     // cell cho biến thể đồng bằng kỳ ảo
    private static final int  FOREST_SUB_CELL   = 150;     // cell cho tiểu-biome rừng

    // ====== Hồ nước ======
    private static final int  LAKE_CELL         = 280;
    private static final int  LAKE_MIN_RADIUS   = 25;      // đường kính 50
    private static final int  LAKE_MAX_RADIUS   = 100;     // đường kính 200
    private static final double LAKE_PROBABILITY = 0.14;   // xác suất 1 cell tạo hồ

    // ====== Sông ======
    private static final int    RIVER_CELL       = 320;
    private static final double RIVER_WIDTH_MIN  = 0.008;
    private static final double RIVER_WIDTH_MAX  = 0.018;

    // ====== Núi ======
    private static final double MOUNTAIN_THRESHOLD       = 0.79;
    private static final double MOUNTAIN_BLOCK_THRESHOLD = 0.89;

    // ====== Làng ======
    private static final int  VILLAGE_CELL      = 420;
    private static final int  VILLAGE_MIN_SIZE  = 12;      // chiều rộng/chiều dài tối thiểu (tile)
    private static final int  VILLAGE_MAX_SIZE  = 30;      // tối đa theo kế hoạch

    public ChunkData generate(int cx, int cy) {
        final int N = ChunkPos.SIZE;
        byte[] l1 = new byte[N * N];
        byte[] l2 = new byte[N * N];
        BitSet coll = new BitSet(N * N);

        long x0 = (long) cx * N;
        long y0 = (long) cy * N;

        Caches caches = new Caches();

        for (int ty = 0; ty < N; ty++) {
            for (int tx = 0; tx < N; tx++) {
                long gx = x0 + tx;
                long gy = y0 + ty;

                TileResult tr = sampleTile(gx, gy, caches);
                int idx = ty * N + tx;
                l1[idx] = (byte) tr.baseId;
                l2[idx] = (byte) tr.overlayId;
                if (tr.blocked) coll.set(idx);
            }
        }

        return new ChunkData(cx, cy, N, l1, l2, coll);
    }

    /** Tạo sampler tái sử dụng được (có cache LRU) cho client-side tra cứu minimap/HUD. */
    public Sampler sampler() { return new Sampler(); }

    /** Mô tả địa hình tại (gx,gy) – tiện lợi cho HUD. */
    public LocationSummary describe(long gx, long gy) {
        return describe(gx, gy, new Caches());
    }

    private TileResult sampleTile(long gx, long gy, Caches caches) {
        Continent cont = continentAt(gx, gy);
        if (cont == null) {
            return new TileResult(BiomeId.OCEAN, OverlayId.NONE, true);
        }

        Region region = regionAt(cont, gx, gy, caches);
        int base = region.baseBiome();
        boolean blocked = false;
        int overlay = OverlayId.NONE;

        Lake lake = lakeAt(cont, gx, gy, caches, base);
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

            VillageCell vc = villageCell(cont, gx, gy, base, blocked, caches);
            if (vc != null) {
                base = BiomeId.VILLAGE;
                overlay = vc.overlayId();
                blocked = vc.blocked();
            }
        }

        return new TileResult(base, overlay, blocked);
    }

    // ===== API mô tả địa danh =====

    private LocationSummary describe(long gx, long gy, Caches caches) {
        Continent cont = continentAt(gx, gy);
        List<GeoFeature> chain = new ArrayList<>();

        if (cont == null) {
            chain.add(oceanFeature(null, gx, gy));
            return new LocationSummary(BiomeId.OCEAN, OverlayId.NONE, true, chain);
        }

        chain.add(oceanFeature(cont.info, gx, gy));
        chain.add(cont.feature);

        Region region = regionAt(cont, gx, gy, caches);
        chain.add(region.feature);

        int base = region.baseBiome();
        boolean blocked = false;
        int overlay = OverlayId.NONE;

        Lake lake = lakeAt(cont, gx, gy, caches, base);
        if (lake != null) {
            chain.add(lake.feature);
            return new LocationSummary(BiomeId.LAKE, OverlayId.NONE, true, chain);
        }

        MountainSample mountain = mountainAt(cont, gx, gy);
        if (mountain != null) {
            chain.add(mountain.feature);
            base = mountain.id;
            blocked = mountain.blocked;
        } else {
            if (isRiver(cont, gx, gy, base)) {
                chain.add(riverFeature(cont, gx, gy));
                return new LocationSummary(BiomeId.RIVER, OverlayId.NONE, true, chain);
            }

            if (base == BiomeId.FOREST) {
                int variant = forestVariant(cont, gx, gy);
                if (variant != BiomeId.FOREST) {
                    chain.add(forestVariantFeature(cont, gx, gy, variant));
                }
                base = variant;
            }

            VillageCell vc = villageCell(cont, gx, gy, base, blocked, caches);
            if (vc != null) {
                chain.add(vc.feature());
                base = BiomeId.VILLAGE;
                overlay = vc.overlayId();
                blocked = vc.blocked();
            }
        }

        return new LocationSummary(base, overlay, blocked, chain);
    }

    // ===== Biome cấp 3 =====

    private Region regionAt(Continent cont, long gx, long gy, Caches caches) {
        long rx = Math.floorDiv(gx - cont.info.cx, REGION_CELL);
        long ry = Math.floorDiv(gy - cont.info.cy, REGION_CELL);

        RegionInfo best = null;
        double bestField = -1e9;
        for (long j = ry - 2; j <= ry + 2; j++) {
            for (long i = rx - 2; i <= rx + 2; i++) {
                RegionInfo info = resolveRegion(cont, i, j, caches.regions);
                double dx = gx - info.cx;
                double dy = gy - info.cy;
                double r = Math.hypot(dx, dy);
                double wobble = (noise(cfg.seed * 0x814B5ABL, (gx + dy) * 0xBF58476D1CE4E5B9L,
                        (gy - dx) * 0x94D049BB133111EBL) - 0.5) * REGION_SHAPE_NOISE;
                double f = 1.0 - (r / info.radius) + wobble;
                if (f > bestField) {
                    bestField = f;
                    best = info;
                }
            }
        }
        return new Region(best, bestField);
    }

    private RegionInfo resolveRegion(Continent cont, long cellX, long cellY, Map<Long, RegionInfo> cache) {
        long key = regionKey(cont.info.id, cellX, cellY);
        RegionInfo cached = cache.get(key);
        if (cached != null) return cached;

        long baseSeed = mix(cfg.seed * 0xE6A36B4DL, cont.info.id ^ cellX, Long.rotateLeft(cellY, 7));

        int radius = REGION_R_MIN + (int) (to01(mix(baseSeed, 0x165667B1L, 0x9E3779B97F4A7C15L))
                * (REGION_R_MAX - REGION_R_MIN));
        int jitter = Math.max(8, REGION_CELL / 2 - radius);
        int ox = (int) ((to01(mix(baseSeed, 0xBF58476D1CE4E5B9L, 0x94D049BB133111EBL)) - 0.5) * 2 * jitter);
        int oy = (int) ((to01(mix(baseSeed, 0x94D049BB133111EBL, 0xBF58476D1CE4E5B9L)) - 0.5) * 2 * jitter);
        long cx = cont.info.cx + cellX * REGION_CELL + REGION_CELL / 2 + ox;
        long cy = cont.info.cy + cellY * REGION_CELL + REGION_CELL / 2 + oy;

        double rel = Math.min(1.0, Math.hypot(cx - cont.info.cx, cy - cont.info.cy) / cont.info.R);
        double inland = 1.0 - rel;
        double climate = valueNoise(cfg.seed * 0x9E3779B97F4A7C15L, cx, cy, 400);
        double humidity = valueNoise(cfg.seed * 0xC2B2AE3DL, cx + cont.info.cx, cy - cont.info.cy, 260);

        double plainBase = clamp(cfg.plainRatio, 0.35, 0.65);
        double forestBase = clamp(cfg.forestRatio, 0.30, 0.55);
        double plainRatio = clamp(plainBase + inland * 0.20 + (climate - 0.5) * 0.05, 0.35, 0.70);
        double forestRatio = clamp(forestBase + (0.5 - inland) * 0.25 + (humidity - 0.5) * 0.18, 0.30, 0.55);
        double desertRatio = clamp(1.0 - plainRatio - forestRatio, 0.05, 0.25);
        double sum = plainRatio + forestRatio + desertRatio;
        plainRatio /= sum;
        forestRatio /= sum;
        desertRatio = 1.0 - plainRatio - forestRatio;

        double roll = to01(mix(baseSeed, 0xD6E8FEBBL, 0xA24BAED4L));
        int biome;
        GeoFeature.Kind kind;
        if (roll < plainRatio) {
            biome = BiomeId.PLAIN;
            kind = GeoFeature.Kind.PLAIN;
        } else if (roll < plainRatio + forestRatio) {
            biome = BiomeId.FOREST;
            kind = GeoFeature.Kind.FOREST;
        } else {
            biome = BiomeId.DESERT;
            kind = GeoFeature.Kind.DESERT;
        }

        int ordinal = 1 + (int) (Math.floorMod(mix(baseSeed, 0x1234ABCDL, 0x5DEECE66DL), 9_000));
        GeoFeature feature = featureForRegion(cont, kind, ordinal);
        RegionInfo info = new RegionInfo(cont.info.id, cx, cy, radius, biome, feature);
        cache.put(key, info);
        return info;
    }

    private GeoFeature featureForRegion(Continent cont, GeoFeature.Kind kind, int ordinal) {
        String code;
        String name;
        switch (kind) {
            case PLAIN -> {
                code = "PLA" + cont.info.ordinal + '-' + ordinal;
                name = composeName(cont.info.id ^ ordinal, PLA_PREFIX, PLA_SUFFIX, "Đồng Bằng");
                return new GeoFeature(3, GeoFeature.Kind.PLAIN, code, "Đồng Bằng " + name);
            }
            case FOREST -> {
                code = "FOR" + cont.info.ordinal + '-' + ordinal;
                name = composeName(cont.info.id ^ ordinal, FOR_PREFIX, FOR_SUFFIX, "Đại Ngàn");
                return new GeoFeature(3, GeoFeature.Kind.FOREST, code, "Rừng " + name);
            }
            case DESERT -> {
                code = "DES" + cont.info.ordinal + '-' + ordinal;
                name = composeName(cont.info.id ^ ordinal, DES_PREFIX, DES_SUFFIX, "Hoang Mạc");
                return new GeoFeature(3, GeoFeature.Kind.DESERT, code, "Sa Mạc " + name);
            }
            default -> throw new IllegalStateException("unexpected kind " + kind);
        }
    }

    private int forestVariant(Continent cont, long gx, long gy) {
        double n = valueNoise(cfg.seed * 0xE6A36B4DL, gx, gy, FOREST_SUB_CELL);
        if (n > 0.93) return BiomeId.FOREST_DARK;
        if (n > 0.88) return BiomeId.FOREST_WEIRD;
        if (n > 0.84) return BiomeId.FOREST_MAGIC; // đã giảm một nửa diện tích biến thể
        if (n > 0.80) return BiomeId.FOREST_FOG;   // Fog nhỏ hơn Magic
        return BiomeId.FOREST;
    }

    private GeoFeature forestVariantFeature(Continent cont, long gx, long gy, int variant) {
        long key = mix(cfg.seed * 0x9E3779B97F4A7C15L, gx, gy);
        String name;
        String code;
        return switch (variant) {
            case BiomeId.FOREST_FOG -> {
                code = "FOG" + cont.info.ordinal + '-' + (1 + (Math.floorMod(key, 7000)));
                name = composeName(key, FOG_PREFIX, FOG_SUFFIX, "Sương Mù");
                yield new GeoFeature(4, GeoFeature.Kind.FOREST_SUB, code, "Rừng Sương Mù " + name);
            }
            case BiomeId.FOREST_MAGIC -> {
                code = "MAG" + cont.info.ordinal + '-' + (1 + (Math.floorMod(key, 7000)));
                name = composeName(key, MAG_PREFIX, MAG_SUFFIX, "Ma Thuật");
                yield new GeoFeature(4, GeoFeature.Kind.FOREST_SUB, code, "Rừng Ma Thuật " + name);
            }
            case BiomeId.FOREST_WEIRD -> {
                code = "ODD" + cont.info.ordinal + '-' + (1 + (Math.floorMod(key, 7000)));
                name = composeName(key, WEIRD_PREFIX, WEIRD_SUFFIX, "Kỳ Quái");
                yield new GeoFeature(4, GeoFeature.Kind.FOREST_SUB, code, "Rừng Kỳ Quái " + name);
            }
            case BiomeId.FOREST_DARK -> {
                code = "DRK" + cont.info.ordinal + '-' + (1 + (Math.floorMod(key, 7000)));
                name = composeName(key, DARK_PREFIX, DARK_SUFFIX, "Hắc Ám");
                yield new GeoFeature(4, GeoFeature.Kind.FOREST_SUB, code, "Rừng Hắc Ám " + name);
            }
            default -> new GeoFeature(4, GeoFeature.Kind.FOREST_SUB, "FOR" + cont.info.ordinal,
                    "Rừng " + composeName(key, FOR_PREFIX, FOR_SUFFIX, "Đại Ngàn"));
        };
    }

    // ===== Hồ =====

    private Lake lakeAt(Continent cont, long gx, long gy, Caches caches, int base) {
        if (base == BiomeId.DESERT) return null; // tránh sa mạc

        long cellX = floorDiv(gx, LAKE_CELL);
        long cellY = floorDiv(gy, LAKE_CELL);
        for (long j = cellY - 1; j <= cellY + 1; j++) {
            for (long i = cellX - 1; i <= cellX + 1; i++) {
                Lake lake = resolveLake(cont, i, j, caches.lakes);
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
        Region region = regionAt(actual, cx, cy, new Caches());
        int centerBiome = region.baseBiome();
        if (centerBiome == BiomeId.DESERT) {
            cache.put(key, Lake.NONE);
            return null;
        }

        int ordinal = 1 + (int) (Math.floorMod(baseSeed, 8_000));
        GeoFeature feature = new GeoFeature(4, GeoFeature.Kind.LAKE,
                "LAK" + cont.info.ordinal + '-' + ordinal,
                "Hồ " + composeName(baseSeed, LAKE_PREFIX, LAKE_SUFFIX, "Bình Minh"));
        Lake lake = new Lake(true, cont.info.id, cx, cy, radius, feature);
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

    private GeoFeature riverFeature(Continent cont, long gx, long gy) {
        long key = mix(cfg.seed * 0xB5297A4DL, gx, gy);
        String code = "RIV" + cont.info.ordinal + '-' + (1 + Math.floorMod(key, 9000));
        String name = composeName(key, RIVER_PREFIX, RIVER_SUFFIX, "Dài");
        return new GeoFeature(4, GeoFeature.Kind.RIVER, code, "Sông " + name);
    }

    // ===== Núi =====

    private MountainSample mountainAt(Continent cont, long gx, long gy) {
        double ridge = ridgeNoise(cfg.seed * 0xD6E8FEBBL, gx * 0xBF58476D1CE4E5B9L, gy * 0x94D049BB133111EBL);
        if (ridge < MOUNTAIN_THRESHOLD) return null;

        double lat = Math.max(cont.info.latNorm, Math.min(1.0, Math.abs((double) gy) / LAT_SCALE));
        double type = noise(cfg.seed * 0xC2B2AE3DL, gx * 0x165667B1L, gy * 0x9E3779B97F4A7C15L);

        int id;
        GeoFeature.Kind kind = GeoFeature.Kind.MOUNTAIN;
        String label;
        if (ridge > 0.96 || lat > 0.75) {
            id = BiomeId.MOUNTAIN_SNOW;
            label = "Tuyết";
        } else if (type > 0.88) {
            id = BiomeId.MOUNTAIN_VOLCANO;
            label = "Lửa";
        } else if (type < 0.42) {
            id = BiomeId.MOUNTAIN_FOREST;
            label = "Rừng";
        } else {
            id = BiomeId.MOUNTAIN_ROCK;
            label = "Đá";
        }

        boolean blocked = ridge > MOUNTAIN_BLOCK_THRESHOLD;
        if (!blocked && ridge > (MOUNTAIN_THRESHOLD + MOUNTAIN_BLOCK_THRESHOLD) * 0.5) {
            long h = mix(cfg.seed * 0x851B3E95L, gx, gy);
            blocked = (h & 3L) == 0L; // tạo chuỗi chắn xen kẽ khe đèo
        }

        long nameSeed = mix(cfg.seed * 0x1234ABCDL, gx, gy);
        String code = "MOU" + cont.info.ordinal + '-' + (1 + Math.floorMod(nameSeed, 9000));
        String name = composeName(nameSeed, MOUNT_PREFIX, MOUNT_SUFFIX, label);
        GeoFeature feature = new GeoFeature(4, GeoFeature.Kind.MOUNTAIN, code, "Dãy Núi " + name);
        return new MountainSample(id, blocked, feature);
    }

    // ===== Làng =====

    private VillageCell villageCell(Continent cont, long gx, long gy, int base,
                                    boolean blocked,
                                    Caches caches) {
        if (blocked) return null;
        if (base != BiomeId.PLAIN && base != BiomeId.PLAIN_WEIRD) return null;

        long cellX = floorDiv(gx, VILLAGE_CELL);
        long cellY = floorDiv(gy, VILLAGE_CELL);
        for (long j = cellY - 1; j <= cellY + 1; j++) {
            for (long i = cellX - 1; i <= cellX + 1; i++) {
                Village village = resolveVillage(cont, i, j, caches.villages);
                if (village != null && village.contains(gx, gy)) {
                    return village.sample(gx, gy);
                }
            }
        }
        return null;
    }

    private Village resolveVillage(Continent cont, long cellX, long cellY, Map<Long, Village> cache) {
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

        Region region = regionAt(actual, cx, cy, new Caches());
        int baseBiome = region.baseBiome();
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

        if (!nearWaterForVillage(cx, cy)) {
            cache.put(key, Village.NONE);
            return null;
        }

        int spacing = 5 + (int) (to01(mix(baseSeed, 0xD6E8FEBBL, 0xC2B2AE3DL)) * 3); // 5..7 ô giữa nhà
        int roadHalf = 1;  // đường rộng 3 ô
        int houseHalf = 1; // nhà 3×3

        int ordinal = 1 + (int) (Math.floorMod(baseSeed, 9_000));
        GeoFeature feature = new GeoFeature(4, GeoFeature.Kind.VILLAGE,
                "VIL" + cont.info.ordinal + '-' + ordinal,
                "Làng " + composeName(baseSeed, VIL_PREFIX, VIL_SUFFIX, "Thịnh Vượng"));
        Village village = new Village(true, cont.info.id, cx, cy, halfW, halfH, spacing, roadHalf, houseHalf, feature);
        cache.put(key, village);
        return village;
    }

    private boolean nearWaterForVillage(long cx, long cy) {
        final int radius = 128;
        final int step = 16;
        Caches caches = new Caches();
        for (int dy = -radius; dy <= radius; dy += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                if (dx * dx + dy * dy > radius * radius) continue;
                long sx = cx + dx;
                long sy = cy + dy;
                Continent c = continentAt(sx, sy);
                if (c == null) return true;
                Region region = regionAt(c, sx, sy, caches);
                int base = region.baseBiome();
                if (lakeAt(c, sx, sy, caches, base) != null) return true;
                if (isRiver(c, sx, sy, base)) return true;
            }
        }
        return false;
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
        return (best > 0 && bestInfo != null) ? new Continent(bestInfo, best, continentFeature(bestInfo)) : null;
    }

    private CInfo continentInfo(long mi, long mj) {
        long h = mix(cfg.seed * 0x9E3779B97F4A7C15L, mi, mj);
        boolean exists = to01(h) >= 0.70;
        if (!exists) {
            return new CInfo(false, 0, 0, 0, mi, mj, 0, 0.0, 0);
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
        int ordinal = 1 + (int) (Math.floorMod(id, 5000));

        return new CInfo(true, cx, cy, R, mi, mj, id, latNorm, ordinal);
    }

    private GeoFeature continentFeature(CInfo info) {
        String code = "LUC" + info.ordinal;
        String name = composeName(info.id, CONT_PREFIX, CONT_SUFFIX, "Bình Minh");
        return new GeoFeature(2, GeoFeature.Kind.CONTINENT, code, "Lục địa " + name);
    }

    private GeoFeature oceanFeature(CInfo info, long gx, long gy) {
        long seed = info != null ? info.id : mix(cfg.seed * 0xD6E8FEBBL, gx, gy);
        int ordinal = info != null ? info.ordinal : (1 + (int) (Math.floorMod(seed, 7000)));
        String code = "SEA" + ordinal;
        String name = composeName(seed, SEA_PREFIX, SEA_SUFFIX, "Thủy Triều");
        return new GeoFeature(1, GeoFeature.Kind.OCEAN, code, "Biển " + name);
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

    private static long regionKey(long id, long cx, long cy) {
        return (id * 0x165667B1L) ^ Long.rotateLeft(cx, 9) ^ Long.rotateLeft(cy, 21);
    }

    private static <T> Map<Long, T> lruMap(int maxEntries) {
        return new LinkedHashMap<Long, T>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, T> eldest) {
                return size() > maxEntries;
            }
        };
    }

    // ===== Data class =====

    private record TileResult(int baseId, int overlayId, boolean blocked) {}

    private record MountainSample(int id, boolean blocked, GeoFeature feature) {}

    private record Continent(CInfo info, double field, GeoFeature feature) {}

    private record Region(RegionInfo info, double field) {
        int baseBiome() { return info.baseBiome; }
        GeoFeature feature() { return info.feature; }
    }

    private record RegionInfo(long continentId, long cx, long cy, int radius, int baseBiome, GeoFeature feature) {}

    private static final class Lake {
        static final Lake NONE = new Lake(false, 0, 0, 0, 0, null);

        final boolean exists;
        final long continentId;
        final long cx, cy;
        final int radius;
        final GeoFeature feature;

        Lake(boolean exists, long continentId, long cx, long cy, int radius, GeoFeature feature) {
            this.exists = exists;
            this.continentId = continentId;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.feature = feature;
        }

        boolean contains(long x, long y) {
            if (!exists) return false;
            long dx = x - cx;
            long dy = y - cy;
            return dx * dx + dy * dy <= (long) radius * radius;
        }
    }

    private static final class Village {
        static final Village NONE = new Village(false, 0, 0, 0, 0, 0, 0, 0, null);

        final boolean exists;
        final long continentId;
        final long cx, cy;
        final int halfW, halfH;
        final int spacing;
        final int roadHalf;
        final int houseHalf;
        final GeoFeature feature;

        Village(boolean exists, long continentId, long cx, long cy,
                int halfW, int halfH, int spacing, int roadHalf, int houseHalf, GeoFeature feature) {
            this.exists = exists;
            this.continentId = continentId;
            this.cx = cx;
            this.cy = cy;
            this.halfW = halfW;
            this.halfH = halfH;
            this.spacing = Math.max(4, spacing);
            this.roadHalf = Math.max(0, roadHalf);
            this.houseHalf = Math.max(1, houseHalf);
            this.feature = feature;
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
                return new VillageCell(OverlayId.ROAD, false, feature);
            }

            int nearestX = Math.round(dx / (float) spacing) * spacing;
            int nearestY = Math.round(dy / (float) spacing) * spacing;
            if (Math.abs(dx - nearestX) <= houseHalf && Math.abs(dy - nearestY) <= houseHalf) {
                return new VillageCell(OverlayId.HOUSE, true, feature);
            }
            return new VillageCell(OverlayId.FARM, false, feature);
        }
    }

    private record VillageCell(int overlayId, boolean blocked, GeoFeature feature) {}

    private record CInfo(boolean exists, long cx, long cy, int R,
                         long cellX, long cellY, long id, double latNorm, int ordinal) {}

    private static final class Caches {
        final Map<Long, RegionInfo> regions;
        final Map<Long, Lake> lakes;
        final Map<Long, Village> villages;

        Caches() {
            this(new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        Caches(Map<Long, RegionInfo> regions, Map<Long, Lake> lakes, Map<Long, Village> villages) {
            this.regions = regions;
            this.lakes = lakes;
            this.villages = villages;
        }
    }

    /**
     * Sampler client-side: dùng cache LRU để tránh OOM khi vẽ minimap.
     */
    public final class Sampler {
        private final Caches caches;

        private Sampler() {
            this.caches = new Caches(lruMap(4096), lruMap(2048), lruMap(1024));
        }

        public TileResult tile(long gx, long gy) {
            return sampleTile(gx, gy, caches);
        }

        public LocationSummary describe(long gx, long gy) {
            return WorldGenerator.this.describe(gx, gy, caches);
        }

        public int baseId(long gx, long gy) {
            return sampleTile(gx, gy, caches).baseId();
        }
    }

    // ===== Name tables =====
    private static final String[] SEA_PREFIX = {"Thủy", "Hải", "Đại Dương", "Bão", "Sóng", "Lam"};
    private static final String[] SEA_SUFFIX = {"Tím", "Ngọc", "Trầm", "Bạc", "Lục", "Vô Tận"};
    private static final String[] CONT_PREFIX = {"Bình Minh", "Hừng Đông", "Phong", "Thiên", "Tịnh", "Hạo"};
    private static final String[] CONT_SUFFIX = {"Sơn", "Long", "Thịnh", "Quang", "Bảo", "Tường"};
    private static final String[] PLA_PREFIX = {"Màu", "Khúc", "Thanh", "Thảo", "An", "Trường"};
    private static final String[] PLA_SUFFIX = {"Nguyên", "Bằng", "Cốc", "Điền", "Lộ", "Lưu"};
    private static final String[] FOR_PREFIX = {"Đại", "Thiên", "Cổ", "U", "Bích", "Thanh"};
    private static final String[] FOR_SUFFIX = {"Lâm", "Sâm", "Thụ", "Lục", "Mộc", "Di"};
    private static final String[] DES_PREFIX = {"Hoả", "Sa", "Hắc", "Kim", "Nhuận", "Liệt"};
    private static final String[] DES_SUFFIX = {"Châu", "Vực", "Mang", "Khô", "Diễm", "Nhiệt"};
    private static final String[] LAKE_PREFIX = {"Thủy", "Vân", "Nguyệt", "Ngọc", "Thiều", "Mộng"};
    private static final String[] LAKE_SUFFIX = {"Trì", "Hồ", "Trạch", "Trầm", "Tuyền", "Âu"};
    private static final String[] RIVER_PREFIX = {"Trường", "Hoàng", "Thanh", "Thiên", "Tuyết", "Thạch"};
    private static final String[] RIVER_SUFFIX = {"Giang", "Xuyên", "Hà", "Thủy", "Lưu", "Lệ"};
    private static final String[] MOUNT_PREFIX = {"Bạch", "Hỏa", "Tùng", "Huyền", "Phong", "Liệt"};
    private static final String[] MOUNT_SUFFIX = {"Sơn", "Nhạc", "Đỉnh", "Vỹ", "Phong", "Nhẫn"};
    private static final String[] VIL_PREFIX = {"Thái", "Tịnh", "Hòa", "An", "Lạc", "Hữu"};
    private static final String[] VIL_SUFFIX = {"Thôn", "Xá", "Trấn", "Trang", "Giáp", "Điền"};
    private static final String[] FOG_PREFIX = {"Sương", "Mù", "Mộng", "Hư", "Ảo", "Lam"};
    private static final String[] FOG_SUFFIX = {"Ảnh", "Vụ", "Mê", "Lộ", "Khúc", "Tản"};
    private static final String[] MAG_PREFIX = {"Pháp", "Huyễn", "Tinh", "Diệu", "Nguyệt", "Tuyền"};
    private static final String[] MAG_SUFFIX = {"Giới", "Thuật", "Vực", "Nguyên", "Dạ", "Quang"};
    private static final String[] WEIRD_PREFIX = {"Tịch", "U", "Quái", "Kỳ", "Ảm", "Hắc"};
    private static final String[] WEIRD_SUFFIX = {"Trắc", "Tùng", "Thạch", "Vân", "Thụ", "Điểu"};
    private static final String[] DARK_PREFIX = {"Hắc", "Bóng", "Âm", "Tịch", "U", "Dạ"};
    private static final String[] DARK_SUFFIX = {"Lâm", "Quang", "Nguyên", "Mộc", "Viễn", "Tùng"};

    private static String composeName(long seed, String[] prefix, String[] suffix, String fallback) {
        if (prefix.length == 0 || suffix.length == 0) return fallback;
        int a = (int) Math.floorMod(seed, prefix.length);
        int b = (int) Math.floorMod(seed >>> 16, suffix.length);
        return prefix[a] + ' ' + suffix[b];
    }
}
