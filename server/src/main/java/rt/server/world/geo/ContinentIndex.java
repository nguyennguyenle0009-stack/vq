package rt.server.world.geo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import rt.common.world.WorldGenConfig;

/** Gán ID & tên cho từng LỤC ĐỊA (thành phần liên thông “đất”) với BFS có giới hạn (anti-OOM). */
public class ContinentIndex {

    public static final class Meta {
        public final int id; public final long ax, ay;
        public String name; public long areaCells = 0;
        Meta(int id,long ax,long ay){ this.id=id; this.ax=ax; this.ay=ay; }
    }

    private final WorldGenConfig cfg;
    private final int cell;
    private final Map<Long,Integer> label = new ConcurrentHashMap<>();
    private final Map<Integer,Meta> metas = new ConcurrentHashMap<>();

    // Giới hạn BFS để tránh OOM
    private static final int  MAX_VISIT_CELLS = 200_000; // tối đa duyệt 200k macro-cells/1 thành phần
    private static final int  MAX_RADIUS_CELLS = 512;    // chỉ flood trong bán kính 512 cell từ điểm hỏi

    public ContinentIndex(WorldGenConfig cfg){ this(cfg, Math.max(256, cfg.biomeScaleTiles)); }
    public ContinentIndex(WorldGenConfig cfg, int macroCellTiles){
        this.cfg = cfg;
        this.cell = macroCellTiles;
    }

    public int cellSizeTiles(){ return cell; }
    public Meta meta(int id){ return metas.get(id); }
    public Collection<Meta> all(){ return metas.values(); }

    private static long key(long cx,long cy){ return (cx<<32) ^ (cy & 0xffffffffL); }

    // dùng cùng continental mask với generator (ở tỉ lệ macro)
    private boolean isLandCell(long cx, long cy){
        long gx = cx * (long)cell, gy = cy * (long)cell;
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        return cont >= cfg.landThreshold;
    }

    /** 0 = biển, >0 = id lục địa. */
    public int idAtTile(long gx, long gy){
        long cx = Math.floorDiv(gx, cell), cy = Math.floorDiv(gy, cell);
        return idAtCell(cx, cy);
    }

    public int idAtCell(long cx, long cy){
        long k = key(cx,cy);
        Integer got = label.get(k);
        if (got != null) return got;

        if (!isLandCell(cx,cy)) { label.put(k, 0); return 0; } // biển

        // BFS có BUDGET + RADIUS
        final long sx = cx, sy = cy;
        ArrayDeque<long[]> q = new ArrayDeque<>();
        HashSet<Long> comp = new HashSet<>();
        q.add(new long[]{cx,cy});
        long minX=cx, minY=cy;
        int visited = 0;

        while(!q.isEmpty()){
            long[] v = q.removeFirst();
            long x=v[0], y=v[1];
            long kk = key(x,y);
            if (comp.contains(kk)) continue;

            // giới hạn bán kính
            if (Math.abs(x - sx) > MAX_RADIUS_CELLS || Math.abs(y - sy) > MAX_RADIUS_CELLS) continue;

            comp.add(kk);
            visited++;
            if (x<minX || (x==minX && y<minY)) { minX=x; minY=y; }

            // budget – nếu đủ lớn thì dừng (ghi nhãn phần đã thấy, phần còn lại sẽ dán nhãn ở lần hỏi sau)
            if (visited >= MAX_VISIT_CELLS) break;

            long[][] nb = {{x+1,y},{x-1,y},{x,y+1},{x,y-1}};
            for (long[] n : nb){
                long nx=n[0], ny=n[1];
                long nk=key(nx,ny);
                if (!comp.contains(nk) && isLandCell(nx,ny)) q.add(new long[]{nx,ny});
            }
        }

        final long ax = minX, ay = minY;
        final int id = (int)(mix(cfg.seed, ax, ay) & 0x7fffffff) | 1;

        Meta meta = metas.computeIfAbsent(id, k2 -> {
            Meta m = new Meta(id, ax, ay);
            m.name = genName(id);
            return m;
        });
        meta.areaCells += comp.size();

        // Ghi nhãn phần đã duyệt để cache
        for (long kk2 : comp) label.put(kk2, id);

        // đảm bảo cell hiện tại có id
        label.put(k, id);
        return id;
    }

    // ===== Helpers =====
    private static double noise(long a,long b,long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11) / (double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x = a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdL;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53L;
        x^=(x>>>33); return x;
    }
    private static String genName(int id){
        String[] A={"Ar","Bel","Cal","Dor","Eri","Fal","Gal","Hel","Ira","Jen","Kar","Lor"};
        String[] B={"ia","or","en","um","eth","ar","on","is","ea","oth","an","el"};
        return A[Math.abs(id)%A.length] + B[(Math.abs(id)/A.length)%B.length];
    }
}
