// rt/common/world/gen/stages/CollisionStage.java
package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.gen.*;

import java.util.Random;

public final class CollisionStage implements ChunkStage {
  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    for (int y=0;y<b.size;y++) for (int x=0;x<b.size;x++){
      int t = b.getL1(x,y);
      boolean block = (t==Terrain.M_ROCK.id) || (t==Terrain.LAKE.id) || (t==Terrain.OCEAN.id);
      b.block(x,y, block);
    }
  }
}
