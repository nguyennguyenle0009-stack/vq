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

    public GeoInfo at(long gx, long gy){
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
}
