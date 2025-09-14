package rt.common.map;

import java.util.ArrayList;
import java.util.List;

public final class LayeredTerrain implements TerrainGeneratorEx {
    private final TerrainGenerator base;
    private final List<ChunkDecorator> decos = new ArrayList<>();
    public LayeredTerrain(TerrainGenerator base){ this.base = base; }
    public LayeredTerrain add(ChunkDecorator d){ decos.add(d); return this; }
    @Override public void configure(TerrainParams p) { }
    @Override public TileChunk generate(long seed,int cx,int cy){
        TileChunk c = base.generate(seed, cx, cy);
        for (ChunkDecorator d : decos) d.apply(seed, cx, cy, c);
        return c;
    }
    public interface ChunkDecorator { void apply(long seed, int cx, int cy, TileChunk chunk); }
}
