package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

/** Bảo hiểm: chỗ nào trong lục địa mà chưa có gì thì lấp PLAIN. */
public final class BasePlainStage implements ChunkStage {
  public BasePlainStage(WorldGenConfig cfg) {}
  
  @Override 
  public void apply(int cx,int cy, Random rng, TileBuffer b){
    for (int y=0;y<b.size;y++)
      for (int x=0;x<b.size;x++)
        if (b.getL1(x,y)==0) b.setL1(x,y, Terrain.PLAIN.id);
  }
}
