package rt.server.world.geo;

import rt.common.world.WorldGenConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Label các khối đại dương (thành phần liên thông của OCEAN) ở lưới macro. */
public final class SeaIndex {
    public static final class Meta {
        public final int id; public final long ax, ay;
        public String name; public long areaCells;
        Meta(int id,long ax,long ay){ this.id=id; this.ax=ax; this.ay=ay; }
    }

    private final WorldGenConfig cfg;
    private final int cell;
    private final Map<Long,Integer> label = new ConcurrentHashMap<>();
    private final Map<Integer,Meta> metas = new ConcurrentHashMap<>();

    public SeaIndex(WorldGenConfig cfg){ this(cfg, Math.max(128, cfg.biomeScaleTiles)); }
    public SeaIndex(WorldGenConfig cfg, int macroCellTiles){ this.cfg=cfg; this.cell=macroCellTiles; }

    public int cellSizeTiles(){ return cell; }
    public Meta meta(int id){ return metas.get(id); }
    public Collection<Meta> all(){ return metas.values(); }
    private static long key(long cx,long cy){ return (cx<<32) ^ (cy & 0xffffffffL); }

    private boolean isOceanCell(long cx, long cy){
        long gx = cx * (long)cell, gy = cy * (long)cell;
        long cgx = Math.floorDiv(gx, cfg.continentScaleTiles);
        long cgy = Math.floorDiv(gy, cfg.continentScaleTiles);
        double cont = noise(cfg.seed*0x9E37L, cgx*0xA24BL, cgy*0x9FB2L);
        return cont < cfg.landThreshold; // OCEAN
    }

    /** 0 nếu không phải biển, ngược lại trả seaId (>0). */
    public int idAtTile(long gx, long gy){
        long cx = Math.floorDiv(gx, cell), cy = Math.floorDiv(gy, cell);
        return idAtCell(cx, cy);
    }

    public int idAtCell(long cx, long cy){
        long k = key(cx,cy);
        Integer got = label.get(k);
        if (got != null) return got;

        if (!isOceanCell(cx,cy)) { label.put(k, 0); return 0; }

        // BFS liên thông theo ocean
        ArrayDeque<long[]> q = new ArrayDeque<>();
        HashSet<Long> comp = new HashSet<>();
        q.add(new long[]{cx,cy});
        long minX=cx, minY=cy;

        while(!q.isEmpty()){
            long[] v=q.removeFirst(); long x=v[0], y=v[1]; long kk=key(x,y);
            if (!comp.add(kk)) continue;
            if (x<minX || (x==minX && y<minY)) { minX=x; minY=y; }
            long[][] nb={{x+1,y},{x-1,y},{x,y+1},{x,y-1}};
            for (long[] n:nb){ long nx=n[0],ny=n[1]; long nk=key(nx,ny);
                if (!comp.contains(nk) && isOceanCell(nx,ny)) q.add(n);
            }
        }

        final long ax=minX, ay=minY;
        final int id = (int)(mix(cfg.seed^0x5EA5EA5EL, ax, ay) & 0x7fffffff) | 1; // >0
        Meta meta = metas.computeIfAbsent(id, k2 -> {
            Meta m = new Meta(id, ax, ay); m.name = genName(id); return m;
        });
        meta.areaCells += comp.size();
        for (long kk : comp) label.put(kk, id);
        return id;
    }

    private static double noise(long a,long b,long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11)/(double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x=a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdL;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53L;
        x^=(x>>>33); return x;
    }
    private static String genName(int id){
        String[] A={"Oc","At","Pa","Ind","Ar","Bor","Ner","Sol","Cal","Mar"};
        String[] B={"ean","lantic","cific","ian","ctic","alis","ion","mare","vion","ith"};
        return A[Math.abs(id)%A.length] + B[(Math.abs(id)/A.length)%B.length];
    }
}
