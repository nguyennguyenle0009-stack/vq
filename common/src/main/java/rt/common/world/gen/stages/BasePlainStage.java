package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.*;

import java.util.Random;

public final class BasePlainStage implements ChunkStage {
  private final WorldGenConfig cfg;
  public BasePlainStage(WorldGenConfig cfg){ this.cfg = cfg; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    // nền mặc định là đồng bằng
    for (int y=0;y<b.size;y++) for (int x=0;x<b.size;x++) {
      if (b.getL1(x,y)==0) b.setL1(x,y, Terrain.PLAIN.id);
    }
  }
}
