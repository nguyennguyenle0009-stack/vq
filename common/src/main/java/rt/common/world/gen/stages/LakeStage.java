package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class LakeStage implements ChunkStage {
  private final WorldGenConfig cfg;
  public LakeStage(WorldGenConfig cfg){ this.cfg = cfg; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    // TODO: thay bằng logic thật (blob lake)
    if (rng.nextDouble()<0.05){
      int cx0 = rng.nextInt(b.size), cy0 = rng.nextInt(b.size), r = Math.max(2, b.size/8);
      for (int y=cy0-r; y<=cy0+r; y++)
        for (int x=cx0-r; x<=cx0+r; x++){
          if (x<0||y<0||x>=b.size||y>=b.size) continue;
          int dx=x-cx0, dy=y-cy0;
          if (dx*dx+dy*dy <= r*r) b.setL1(x,y, Terrain.LAKE.id);
        }
    }
  }
}
