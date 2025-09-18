package rt.server.world.geo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import rt.common.world.WorldGenConfig;

/** Gán ID & tên cho từng LỤC ĐỊA (thành phần liên thông của “đất”).
 *  Làm lười (lazy): chỉ label vùng nào có truy vấn. Deterministic theo seed.
 */
public class ContinentIndex {

    public static final class Meta {
        public final int id;
        public final long ax, ay;          // anchor (cell macro) ổn định
        public String name;
        public long areaCells = 0;         // ước lượng theo số cell macro
        Meta(int id, long ax, long ay){ this.id=id; this.ax=ax; this.ay=ay; }
    }

    private final WorldGenConfig cfg;
    private final int cell;                // kích cỡ 1 cell macro, đơn vị tile
    private final Map<Long,Integer> label = new ConcurrentHashMap<>();
    private final Map<Integer,Meta> metas = new ConcurrentHashMap<>();
    @SuppressWarnings("unused")
    private final Random rnd;

    public ContinentIndex(WorldGenConfig cfg) {
        // mặc định: cell ≈ biomeScaleTiles (đủ to để quét nhanh)
        this(cfg, Math.max(128, cfg.biomeScaleTiles));
    }
    public ContinentIndex(WorldGenConfig cfg, int macroCellTiles){
        this.cfg = cfg;
        this.cell = macroCellTiles;
        this.rnd  = new Random(cfg.seed ^ 0xC0FFEE);
    }

    public int cellSizeTiles(){ return cell; }
    public Meta meta(int id){ return metas.get(id); }
    public Collection<Meta> all(){ return metas.values(); }

    private static long key(long cx,long cy){ return (cx<<32) ^ (cy & 0xffffffffL); }

    // dùng cùng continental mask với generator (ở tỉ lệ macro)
    private boolean isLandCell(long cx, long cy){
        long gx = cx * (long)cell;
        long gy = cy * (long)cell;
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        return cont >= cfg.landThreshold;
    }

    /** Trả ID lục địa tại tile (gx,gy). 0 = biển. */
    public int idAtTile(long gx, long gy){
        long cx = Math.floorDiv(gx, cell);
        long cy = Math.floorDiv(gy, cell);
        return idAtCell(cx, cy);
    }

    /** Label nếu cần và trả ID tại cell macro (cx,cy). */
    public int idAtCell(long cx, long cy){
        long k = key(cx,cy);
        Integer got = label.get(k);
        if (got != null) return got;

        if (!isLandCell(cx,cy)) { label.put(k, 0); return 0; } // biển

        // BFS flood fill component “đất”
        ArrayDeque<long[]> q = new ArrayDeque<>();
        HashSet<Long> comp = new HashSet<>();
        q.add(new long[]{cx,cy});
        long minX=cx, minY=cy;
        
        

        while(!q.isEmpty()){
            long[] v = q.removeFirst();
            long x=v[0], y=v[1];
            long kk = key(x,y);
            if (!comp.add(kk)) continue;

            if (x<minX || (x==minX && y<minY)) { minX=x; minY=y; }

            long[][] nb = {{x+1,y},{x-1,y},{x,y+1},{x,y-1}};
            for (long[] n : nb){
                long nx=n[0], ny=n[1];
                long nk=key(nx,ny);
                if (!comp.contains(nk) && isLandCell(nx,ny)) q.add(n);
            }
        }
        
        final long ax = minX;
        final long ay = minY;

        // id determinisitc theo anchor + seed
        final int id = (int)(mix(cfg.seed, ax, ay) & 0x7fffffff) | 1;  // >0

        Meta meta = metas.computeIfAbsent(id, k2 -> {
            Meta m = new Meta(id, ax, ay);
            m.name = genName(id);
            return m;
        });

        // giờ meta đã tồn tại; có thể cộng dồn diện tích, gán nhãn...
        meta.areaCells += comp.size();
        for (long kk : comp) label.put(kk, id);
        return id;

//        // ID deterministic theo anchor + seed
//        int id = (int)(mix(cfg.seed, minX, minY) & 0x7fffffff) | 1; // >0
//        Meta meta = metas.computeIfAbsent(id, k2 -> {
//            Meta m = new Meta(id, minX, minY);
//            m.name = genName(id);
//            return m;
//        });
//        meta.areaCells += comp.size();
//        for (long kk : comp) label.put(kk, id);
//        return id;
    }

    // ===== Helpers: noise/mix (copy từ generator) =====
    private static double noise(long a, long b, long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11) / (double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x = a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdL;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53L;
        x^=(x>>>33); return x;
    }

    private String genName(int id){
        String[] A={"Ar","Bel","Cal","Dor","Eri","Fal","Gal","Hel","Ira","Jen","Kar","Lor"};
        String[] B={"ia","or","en","um","eth","ar","on","is","ea","oth","an","el"};
        return A[Math.abs(id)%A.length] + B[(Math.abs(id)/A.length)%B.length];
    }
}
