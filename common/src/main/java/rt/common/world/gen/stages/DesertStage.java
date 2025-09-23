package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class DesertStage implements ChunkStage {
  private final WorldGenConfig cfg;
  public DesertStage(WorldGenConfig cfg){ this.cfg = cfg; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    // TODO: thay bằng phân bố thật theo cfg
    for (int y=0;y<b.size;y++) for (int x=0;x<b.size;x++){
      if (b.getL1(x,y)==Terrain.PLAIN.id && rng.nextDouble()<0.02)
        b.setL1(x,y, Terrain.DESERT.id);
    }
  }
}
