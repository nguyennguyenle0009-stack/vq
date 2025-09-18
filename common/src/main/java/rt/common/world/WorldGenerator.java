package rt.common.world;

import java.util.BitSet;

/** Phase-1: Ocean -> Continents -> {Plain/Forest/Desert} + sprinkle Mountain (solid).
 *  Deterministic per seed; stateless; works per-chunk.
 *
 *  ID dùng như repo hiện tại:
 *   0 = OCEAN (blocked), 2 = PLAIN, 3 = FOREST, 4 = DESERT, 5 = MOUNTAIN (blocked)
 */
public final class WorldGenerator {
    private final WorldGenConfig cfg;

    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    // --- Tham số lục địa
    private static final int  CONT_CELL = 12_000;     // kích thước ô macro (khoảng cách tối thiểu giữa lục địa)
    private static final int  R_MIN     = 310;        // bán kính ~ sqrt(area/π)  với area ≈ 300k
    private static final int  R_MAX     = 490;        // bán kính cho area ≈ 750k
    private static final int  R_SPAN    = R_MAX - R_MIN + 1;
    private static final int  JITTER    = 500;        // lệch tâm trong ô macro
    private static final double LAND_SHAPE_NOISE = 0.18; // méo bờ biển tự nhiên

    // --- Tham số phụ
    private static final double MOUNTAIN_RIDGE = 0.83;     // ngưỡng rải núi (blocked)
    private static final double DESERT_BIAS_NEAR_WATER = 0.04; // giảm sa mạc sát bờ

    public ChunkData generate(int cx, int cy) {
        final int N = ChunkPos.SIZE;
        byte[] l1 = new byte[N*N], l2 = new byte[N*N];
        java.util.BitSet coll = new java.util.BitSet(N*N);

        for (int ty=0; ty<N; ty++) for (int tx=0; tx<N; tx++){
            long gx = (long)cx*N + tx, gy = (long)cy*N + ty;
            int idx = ty*N + tx;

            // ===== Continental mask (macro): scale theo continentScaleTiles
            long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
            long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
            double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
            if (cont < cfg.landThreshold) {  // ocean
                l1[idx] = (byte) rt.common.world.Terrain.OCEAN.id;
                coll.set(idx);               // biển bị chặn
                continue;
            }

            // ===== Biome (macro vừa): scale theo biomeScaleTiles
            long bgx = Math.floorDiv(gx, cfg.biomeScaleTiles);
            long bgy = Math.floorDiv(gy, cfg.biomeScaleTiles);
            double bio = noise(cfg.seed*0x94D0L, bgx*0xC2B2L, bgy*0x1656L);
            if (bio < cfg.plainRatio)                           l1[idx] = (byte) rt.common.world.Terrain.PLAIN.id;
            else if (bio < cfg.plainRatio + cfg.forestRatio)    l1[idx] = (byte) rt.common.world.Terrain.FOREST.id;
            else                                                l1[idx] = (byte) rt.common.world.Terrain.DESERT.id;

            // ===== Mountain (fine)
            long mgx = Math.floorDiv(gx, cfg.mountainScaleTiles);
            long mgy = Math.floorDiv(gy, cfg.mountainScaleTiles);
            double m = noise(cfg.seed*0xD6E8L, mgx*0xBF58L, mgy*0x94D0L);
            if (m > cfg.mountainThreshold) {
                l1[idx] = (byte) rt.common.world.Terrain.MOUNTAIN.id;
                coll.set(idx);
            }
        }
        return new ChunkData(cx, cy, N, l1, l2, coll);
    }


    // ====== Continent field ======
    private static final class Continent { final double field; Continent(double f){field=f;} }

    /** null nếu ocean; ngược lại field > 0 (0..~1) càng sâu trong lục địa càng lớn. */
    private Continent continentAt(long gx, long gy){
        long mi = Math.floorDiv(gx, CONT_CELL);
        long mj = Math.floorDiv(gy, CONT_CELL);

        double best = -1e9;
        for (long j = mj-1; j <= mj+1; j++) {
            for (long i = mi-1; i <= mi+1; i++) {
                CInfo info = continentInfo(i, j);
                if (!info.exists) continue;

                long dx = gx - info.cx, dy = gy - info.cy;
                double r = Math.hypot(dx, dy);

                // méo theo noise để bờ biển tự nhiên
                double wobble = (noise(cfg.seed*0xA24BL, (gx+dy)*0x9FB2L, (gy-dx)*0xB4B5L)-0.5) * LAND_SHAPE_NOISE;
                double f = 1.0 - (r / info.R) + wobble; // >0 => trong lục địa

                if (f > best) best = f;
            }
        }
        return (best > 0) ? new Continent(best) : null;
    }

    private static final class CInfo { boolean exists; long cx, cy; int R; }

    /** Thông tin tâm & bán kính lục địa của một ô macro. Deterministic. */
    private CInfo continentInfo(long mi, long mj){
        long h = mix(cfg.seed*0x9E37L, mi, mj);
        CInfo c = new CInfo();

        // 70% ô không có lục địa (thưa, đúng yêu cầu không san sát)
        if (to01(h) < 0.70) { c.exists = false; return c; }
        c.exists = true;

        // jitter tâm trong ô macro
        long hx = mix(h, 0x1234ABCDL, 0xCAFEBABEL);
        long hy = mix(h, 0x9E3779B97F4A7C15L, 0xD1B54A32D192ED03L);
        int jx = (int)((to01(hx) - 0.5) * 2 * JITTER);
        int jy = (int)((to01(hy) - 0.5) * 2 * JITTER);

        c.cx = mi * CONT_CELL + CONT_CELL/2 + jx;
        c.cy = mj * CONT_CELL + CONT_CELL/2 + jy;

        long hr = mix(h, 0xA24BAED4L, 0x165667B1L);
        c.R = R_MIN + (int)(to01(hr) * R_SPAN);
        return c;
    }

    // ===== noise helpers (deterministic, cross-platform) =====
    private static double ridgeNoise(long a, long b, long c){
        double n = noise(a,b,c);
        return 1.0 - 2.0 * Math.abs(n - 0.5); // ridged
    }

    private static double noise(long a, long b, long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11) / (double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x = a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdl;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53l;
        x^=(x>>>33); return x;
    }
    private static double clamp01(double v){ return v<0?0:(v>1?1:v); }
}
