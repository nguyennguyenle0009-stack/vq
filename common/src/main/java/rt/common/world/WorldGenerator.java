package rt.common.world;

import java.util.BitSet;

public final class WorldGenerator {
    private final WorldGenConfig cfg;

    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    // ===== API giữ nguyên =====
    public int idAt(int gx, int gy)     { return idAt((long)gx, (long)gy); }
    public int idAt(long gx, long gy)   { return evalTileId(gx, gy); }
    public int idAtTile(int gx, int gy) { return idAt(gx, gy); }

    public ChunkData generate(int cx, int cy) {
        final int N = ChunkPos.SIZE;
        byte[] l1 = new byte[N*N], l2 = new byte[N*N];
        BitSet coll = new BitSet(N*N);

        for (int ty=0; ty<N; ty++) for (int tx=0; tx<N; tx++) {
            long gx = (long)cx*N + tx, gy = (long)cy*N + ty;
            int id = evalTileId(gx, gy);
            int idx = ty*N + tx;
            l1[idx] = (byte) id;
            if (Terrain.byId(id).blocked) coll.set(idx);
        }
        return new ChunkData(cx, cy, N, l1, l2, coll);
    }

    // ===== CORE: không chồng lấn =====
    private int evalTileId(long gx, long gy) {
        if (isOcean(gx, gy))    return Terrain.OCEAN.id;     // 0) biển
        if (isMountain(gx, gy)) return Terrain.M_ROCK.id;    // 1) núi ~10%
        if (isLakeProvince(gx, gy)) return Terrain.LAKE.id;  // 2) hồ ~10%
        return regionBiomeId(gx, gy);                        // 3) còn lại: plain/forest/desert
    }

    // ===== Biome theo "tỉnh" & cụm =====
    private int regionBiomeId(long gx, long gy) {
        final int S = Math.max(32, cfg.regionScaleTiles); // kích thước tỉnh
        long rx = Math.floorDiv(gx, S);
        long ry = Math.floorDiv(gy, S);

        final int G  = provincesPerBlock();               // số tỉnh mỗi "block lục địa"
        long bx = Math.floorDiv(rx, G);
        long by = Math.floorDiv(ry, G);

        // Rừng đặc biệt: mỗi loại đúng 2 cụm / block
        boolean inFog   = inCluster(rx, ry, bx, by, 0xF0F0L, 2, radiusForShare(G, 0.05, 2), G);
        boolean inMagic = inCluster(rx, ry, bx, by, 0xA11CL, 2, radiusForShare(G, 0.05, 2), G);
        boolean inDark  = inCluster(rx, ry, bx, by, 0xD4A7L, 2, radiusForShare(G, 0.05, 2), G);

        // Sa mạc: 1–3 cụm rất to / block, tổng ~10%
        int desertClusters = 1 + (int)((mix(cfg.seed*0xDE57L, bx, by) >>> 8) % 3); // 1..3
        boolean allowDesert = inCluster(rx, ry, bx, by, 0xDE57L,
                                        desertClusters, radiusForShare(G, 0.10, desertClusters), G);

        // Trọng số sau khi đã claim núi & hồ:
        final double wForestAll = 0.35;              // 20% ordinary + 3×5% special
        final double wDesert    = allowDesert ? 0.10 : 0.0;
        final double wPlain     = 1.0 - wForestAll - wDesert; // ≈ 0.55 hoặc 0.45 nếu có sa mạc

        long   h = mix(cfg.seed*0xB4B4L, rx, ry);
        double r = to01(h);

        if (r < wForestAll) {
            if (inFog)   return Terrain.F_FOG.id;
            if (inMagic) return Terrain.F_MAGIC.id;
            if (inDark)  return Terrain.F_DARK.id;
            return Terrain.FOREST.id; // rừng thường
        }
        r -= wForestAll;

        if (r < wDesert) return Terrain.DESERT.id;
        return Terrain.PLAIN.id;
    }

    // ===== Hồ nội địa theo "tỉnh": ~10% đất, 2 cụm rất to =====
    private boolean isLakeProvince(long gx, long gy) {
        final int S = Math.max(32, cfg.regionScaleTiles);
        long px = Math.floorDiv(gx, S), py = Math.floorDiv(gy, S);
        final int G = provincesPerBlock();
        long bx = Math.floorDiv(px, G), by = Math.floorDiv(py, G);

        // 2 cụm hồ; bán kính tính để xấp xỉ 10%
        int r = radiusForShare(G, 0.10, 2);
        return inCluster(px, py, bx, by, 0x1A2E3L, 2, r, G);
    }

    // ===== Ocean / Mountain =====
    private boolean isOcean(long gx, long gy) {
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        return cont < cfg.landThreshold;
    }

    private boolean isMountain(long gx, long gy) {
        long mgx = Math.floorDiv(gx, cfg.mountainScaleTiles);
        long mgy = Math.floorDiv(gy, cfg.mountainScaleTiles);
        double ridged = ridgedNoise(cfg.seed*0xD6E8L, mgx*0xBF58L, mgy*0x94D0L);
        return ridged > cfg.mountainThreshold;
    }

    // ====== Cluster utilities ======
    private int provincesPerBlock() {
        int S = Math.max(32, cfg.regionScaleTiles);
        return Math.max(12, cfg.continentScaleTiles / S); // ~23–26 khi S≈256, cont≈6000
    }

    // bán kính (đơn vị tỉnh) để phủ fShare (0..1) với 'count' cụm trên lưới G×G tỉnh
    private int radiusForShare(int G, double fShare, int count) {
        double r = G * Math.sqrt(Math.max(0.0, fShare) / Math.max(1, count) / Math.PI);
        return Math.max(1, (int)Math.round(r));
    }

    // kiểm tra (px,py) có nằm trong 1 trong các tâm cụm của block (bx,by) không
    private boolean inCluster(long px, long py, long bx, long by,
                              long salt, int count, int radius, int G) {
        long h = mix(cfg.seed * salt, bx, by);
        for (int i = 0; i < count; i++) {
            int ox = (int)((h >>> (i*11)) & 0x7FF) % G;
            int oy = (int)((h >>> (i*11+16)) & 0x7FF) % G;
            long cx = bx * G + ox, cy = by * G + oy;
            long dx = px - cx, dy = py - cy;
            if (dx*dx + dy*dy <= (long)radius * radius) return true;
        }
        return false;
    }

    // ===== noise helpers =====
    private static double ridgedNoise(long a,long b,long c){
        double n = noise(a,b,c);
        return 1.0 - 2.0 * Math.abs(n - 0.5);
    }
    private static double noise(long a, long b, long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11) / (double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x = a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdl;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53L;
        x^=(x>>>33); return x;
    }
}
