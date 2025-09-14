package rt.server.world;

import rt.common.map.*;
import java.util.*;

public final class WorldRegistry {

    public static final class Spec {
        public String id;
        public String type = "noise";
        public long seed;
        public String tileset = "/tiles/overworld.png";
        public int tilesetCols = 16;
        public Map<String, Double> params = Collections.emptyMap();
    }

    public static final class WorldCtx {
        public final long seed;
        public final String tileset;
        public final int tilesetCols;
        public final ChunkManager chunks;
        WorldCtx(long seed, String tileset, int tilesetCols, ChunkManager chunks){
            this.seed = seed; this.tileset = tileset; this.tilesetCols = tilesetCols; this.chunks = chunks;
        }
    }

    private final Map<String, WorldCtx> worlds = new HashMap<>();
    private final String defaultWorld;
    private final int viewDist;

    public WorldRegistry(List<Spec> specs, String defaultWorld, int viewDist){
        for (Spec s : specs){
            TerrainGenerator gen = GeneratorFactory.create(s.type, new TerrainParams(s.params, Collections.emptyMap()));
            worlds.put(s.id, new WorldCtx(s.seed, s.tileset, s.tilesetCols, new ChunkManager(s.seed, gen)));
        }
        this.defaultWorld = defaultWorld;
        this.viewDist = viewDist;
    }

    public WorldCtx get(String id){ return worlds.get(id); }
    public String defaultWorld(){ return defaultWorld; }
    public int viewDist(){ return viewDist; }
    public Set<String> ids(){ return Collections.unmodifiableSet(worlds.keySet()); }
}
