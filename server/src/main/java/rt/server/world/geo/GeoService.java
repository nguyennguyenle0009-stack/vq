package rt.server.world.geo;

import rt.common.world.*;

public final class GeoService {
    private final WorldGenerator gen;
    private final ContinentIndex continents;
    private final SeaIndex seas;

    public GeoService(WorldGenConfig cfg) {
        this.gen = new WorldGenerator(cfg);
        this.continents = new ContinentIndex(cfg);
        this.seas = new SeaIndex(cfg);
    }
    public GeoService(WorldGenerator gen, ContinentIndex c, SeaIndex s) {
        this.gen = gen; this.continents = c; this.seas = s;
    }

    public static final class GeoInfo {
        public final int terrainId; public final String terrainName;
        public final int continentId; public final String continentName;
        public final int seaId; public final String seaName;
        public GeoInfo(int tid,String tname,int cid,String cname,int sid,String sname){
            this.terrainId=tid; this.terrainName=tname;
            this.continentId=cid; this.continentName=cname;
            this.seaId=sid; this.seaName=sname;
        }
    }

    public GeoInfo atDemo(long gx, long gy){
        int tid = gen.idAt((int)gx,(int)gy);
        String tname = Terrain.byId(tid).name;

        int sea = 0, cont = 0; String seaName=null, contName=null;
        if (tid == Terrain.OCEAN.id) {
            sea = seas.idAtTile(gx, gy);
            var sm = seas.meta(sea);
            seaName = sm!=null? sm.name : null;
            cont = -1; contName = null;
        } else {
            cont = continents.idAtTile(gx, gy);
            var cm = continents.meta(cont);
            contName = cm!=null? cm.name : null;
            sea = 0; seaName = null;
        }
        return new GeoInfo(tid, tname, cont, contName, sea, seaName);
    }
    
 // GeoService.java

    private static long k(long gx, long gy) { return (gx << 32) ^ (gy & 0xffffffffL); }

    private final java.util.Map<Long, GeoInfo> lru =
        new java.util.LinkedHashMap<>(8192, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Long, GeoInfo> e) {
                return size() > 8192;
            }
        };

    private static String nz(String s) { return s == null ? "—" : s; }

    public synchronized GeoInfo at(long gx, long gy) {
        // SAFE: cache theo TỪNG TILE để không sai ở ven bờ
        final long kk = k(gx, gy);
        GeoInfo hit = lru.get(kk);
        if (hit != null) return hit;

        // Terrain luôn theo tile
        final int tid = gen.idAt((int) gx, (int) gy);
        final Terrain t = Terrain.byId(tid);
        final String tname = nz(t != null ? t.name : null); // phòng thủ nếu tid lạ

        int seaId = 0, contId = 0;
        String seaName = null, contName = null;

        if (tid == Terrain.OCEAN.id) {
            seaId = seas.idAtTile(gx, gy);
            var sm = seas.meta(seaId);
            seaName = sm != null ? sm.name : null;
            contId = 0;              // không có lục địa khi ở biển
            contName = null;
        } else {
            contId = continents.idAtTile(gx, gy);
            var cm = continents.meta(contId);
            contName = cm != null ? cm.name : null;
            seaId = 0;               // không có biển khi ở đất
            seaName = null;
        }

        // Chuẩn hóa: TUYỆT ĐỐI không để null lọt ra ngoài
        GeoInfo info = new GeoInfo(
            tid,
            tname,
            contId,
            nz(contName),
            seaId,
            nz(seaName)
        );

        lru.put(kk, info);
        return info;
    }

}
