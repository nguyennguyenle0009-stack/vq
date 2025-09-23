package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class RiverStage implements ChunkStage {
  private final WorldGenConfig cfg;
  public RiverStage(WorldGenConfig cfg){ this.cfg = cfg; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    // TODO: thay bằng spline/noise. Tạm kẻ 1–2 dải mỏng
    if (rng.nextDouble()<0.15){
      int x = rng.nextInt(b.size);
      for (int y=0;y<b.size;y++){
        if (x>=0 && x<b.size) b.setL1(x,y, Terrain.RIVER.id);
        if (rng.nextDouble()<0.4) x += rng.nextBoolean()?1:-1;
      }
    }
  }
}
