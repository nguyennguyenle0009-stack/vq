package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class CollisionStage implements ChunkStage {
    @Override public void apply(int cx, int cy, Random rng, TileBuffer b){
        for (int y=0;y<b.size;y++){
            for (int x=0;x<b.size;x++){
                int id = b.getL1(x,y);
                Terrain t = Terrain.byId(id);
                boolean block = (t != null) && t.blocked;
                b.block(x,y, block);
            }
        }
    }
}
