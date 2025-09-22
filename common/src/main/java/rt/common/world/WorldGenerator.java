package rt.common.world;

import java.util.BitSet;

public final class WorldGenerator {
    private final WorldGenConfig cfg;

    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    // ===== API giữ nguyên cho GeoService/Overlay =====
    public int idAt(int gx, int gy)            { return idAt((long)gx, (long)gy); }
    public int idAt(long gx, long gy)          { return evalTileId(gx, gy); }
    public int idAtTile(int gx, int gy)        { return idAt(gx, gy); }

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

    // ===== CORE: không chồng lấn, vùng lớn, không RIVER/biển nội địa =====
    private int evalTileId(long gx, long gy) {
        // 0) Lục địa/biển – GIỮ NGUYÊN
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        if (cont < cfg.landThreshold) return Terrain.OCEAN.id; // biển khơi

        // 1) Núi (chỉ M_ROCK) theo ridged
        long mgx = Math.floorDiv(gx, cfg.mountainScaleTiles);
        long mgy = Math.floorDiv(gy, cfg.mountainScaleTiles);
        double ridged = ridgedNoise(cfg.seed*0xD6E8L, mgx*0xBF58L, mgy*0x94D0L);
        if (ridged > cfg.mountainThreshold) return Terrain.M_ROCK.id;

        // 2) Hồ nội địa (Poisson/elip) – thay thế mọi “biển lọt trong lục địa”
        if (isLake(gx, gy)) return Terrain.LAKE.id;

        // 3) Biome nền THEO VÙNG (region) – mỗi vùng 1 biome duy nhất
        //int baseId = regionBiomeId(gx, gy);
        int baseId = provinceBiomeId(gx, gy);

        // Không có bước “bờ biển cát”, không overlay, không river
        return baseId;
    }

    // ===== VÙNG LỚN: Voronoi jitter theo regionScaleTiles =====
    private int regionBiomeId(long gx, long gy) {
        final int S = cfg.regionScaleTiles;              // mặc định 64 → 4096 ô/vùng
        long rx = Math.floorDiv(gx, S);
        long ry = Math.floorDiv(gy, S);

        // chọn seed gần nhất trong 2x2 ô lân cận (jitter mỗi ô)
        long bestIx = rx, bestIy = ry;
        long bestDx = 0, bestDy = 0;
        long bestD2 = Long.MAX_VALUE;

        for (long oy = 0; oy <= 1; oy++) {
            for (long ox = 0; ox <= 1; ox++) {
                long cx = rx + ox;
                long cy = ry + oy;
                long h = mix(cfg.seed*0xA5A5L, cx, cy);
                int jx = (int)((h       & 0xFFFF) % S);   // jitter trong [0..S-1]
                int jy = (int)(((h>>16) & 0xFFFF) % S);
                long sx = cx*S + jx;
                long sy = cy*S + jy;
                long dx = gx - sx, dy = gy - sy;
                long d2 = dx*dx + dy*dy;
                if (d2 < bestD2) { bestD2 = d2; bestIx = cx; bestIy = cy; bestDx = dx; bestDy = dy; }
            }
        }

        // Chọn biome cho seed (ổn định) — dùng tỷ lệ toàn cục, tránh DESERT gần nước
        long sh = mix(cfg.seed*0xB4B4L, bestIx, bestIy);
        double r = to01(sh);
        // moisture macro để giảm DESERT gần bờ (mượt, không tốn)
        long bgx = Math.floorDiv(bestIx*S, cfg.biomeScaleTiles);
        long bgy = Math.floorDiv(bestIy*S, cfg.biomeScaleTiles);
        double moisture = noise(cfg.seed*0xB529L, bgx*0xC2B2L, bgy*0x1656L);

        double total = cfg.targetPlainPct + cfg.targetForestPct + cfg.targetDesertPct;
        double pPlain  = cfg.targetPlainPct  / total;
        double pForest = cfg.targetForestPct / total;
        double pDesert = 1.0 - pPlain - pForest;

        // giảm DESERT nếu ẩm
        pDesert = clamp01(pDesert - (moisture-0.5)*0.25);
        pPlain  = clamp01(1.0 - (pForest + pDesert));

        return (r < pPlain) ? Terrain.PLAIN.id
             : (r < pPlain + pForest) ? Terrain.FOREST.id
             : Terrain.DESERT.id;
    }

    // ===== Hồ nội địa (Poisson-ish elip) =====
    private boolean isLake(long gx, long gy) {
        long cs = cfg.lakeMacroTiles; // ~256
        long cx = Math.floorDiv(gx, cs);
        long cy = Math.floorDiv(gy, cs);
        long h  = mix(cfg.seed*0xA1B2C3DL, cx, cy);

        // tâm elip
        long ox = (h & 0xFFFF) % cs;
        long oy = ((h>>>16) & 0xFFFF) % cs;
        long sx = cx * cs + ox;
        long sy = cy * cs + oy;

        // bán trục
        int rx = 40 + (int)((((h>>>32) & 0xFF)/255.0) * 120); // 40..160
        int ry = 30 + (int)((((h>>>40) & 0xFF)/255.0) * 170); // 30..200

        // spacing: chỉ giữ “seed mạnh nhất” trong 3×3
        long best = h;
        for (long j=-1;j<=1;j++) for (long i=-1;i<=1;i++){
            long hh = mix(cfg.seed*0xA1B2C3DL, cx+i, cy+j);
            if (hh > best) best = hh;
        }
        if (h != best) return false;

        // bên trong lục địa thì là lake; nếu lỡ mask cho OCEAN thì ép về lake
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        if (noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L) < cfg.landThreshold) return false;

        long dx = gx - sx, dy = gy - sy;
        double v = (dx*dx)/(double)(rx*rx) + (dy*dy)/(double)(ry*ry);
        return v <= 1.0;
    }
    
 // Mỗi tỉnh (province) 1 biome, size ~ cfg.provinceScaleTiles^2
    private int provinceBiomeId(long gx, long gy){
        final int S = cfg.provinceScaleTiles;     // 224..256 → 30k–70k ô/tỉnh
        long px = Math.floorDiv(gx, S);
        long py = Math.floorDiv(gy, S);

        // random ổn định cho tỉnh
        long h = mix(cfg.seed*0xB4B4L, px, py);
        double r = to01(h);

        // Ẩm/khô ở tâm tỉnh
        long cx = px*S + S/2, cy = py*S + S/2;
        long bgx = Math.floorDiv(cx, cfg.biomeScaleTiles);
        long bgy = Math.floorDiv(cy, cfg.biomeScaleTiles);
        double moisture = noise(cfg.seed*0xB529L, bgx*0xC2B2L, bgy*0x1656L);

        // Độ khô thô theo tỉnh + buffer xa bờ
        double dryness = noise(cfg.seed*0xD00DL, px*0x9E37L, py*0x94D0L); // 0..1
        int coastD = coastDistanceTiles(cx, cy, 256);
        boolean farFromCoast = (coastD < 0) || (coastD > 96);            // ≥ ~100 ô cách bờ
        boolean allowDesert  = dryness > 0.68 && moisture < 0.45 && farFromCoast;

        // Trọng số theo mục tiêu, loại bỏ DESERT nếu không được phép
        double pP = cfg.targetPlainPct;
        double pF = cfg.targetForestPct;
        double pD = allowDesert ? cfg.targetDesertPct : 0.0;

        double total = pP + pF + pD;
        double wF = pF / total;
        double wD = pD / total;

        // Ưu tiên rừng một chút để tăng xác suất tỉnh rừng
        wF = Math.min(0.95, wF + 0.05);
        double wP = 1.0 - wF - wD;

        if (r < wF) return Terrain.FOREST.id;
        if (r < wF + wD) return Terrain.DESERT.id;
        return Terrain.PLAIN.id;
    }


    // ---- Helpers ----
    private int coastDistanceTiles(long gx, long gy, int max){
        // BFS-lite: check rings up to max tiles to see if touches ocean
        if (max <= 0) return -1;
        for (int d=0; d<=max; d++){
            for (int dx=-d; dx<=d; dx++){
                int dy = d - Math.abs(dx);
                if (isOcean(gx+dx, gy+dy) || isOcean(gx+dx, gy-dy)){
                    return d;
                }
            }
        }
        return -1;
    }
    private boolean isOcean(long gx, long gy){
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        return cont < cfg.landThreshold;
    }

    private static double latFactor(long gy){
        // normalize latitude to 0..1 over large band
        double t = (Math.sin(gy * 2e-5) + 1.0) * 0.5;
        return t;
    }
    private static double edgeFactor(long bx,long by){
        // cheap gradient magnitude of biome noise
        double c = noise(0x12345678L, bx, by);
        double dx = Math.abs(noise(0x12345678L, bx+1, by) - c);
        double dy = Math.abs(noise(0x12345678L, bx, by+1) - c);
        return Math.min(1.0, (dx+dy)*4.0);
    }


    // ===== Helpers noise =====
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
    private static double clamp01(double v){ return v<0?0:(v>1?1:v); }
}
