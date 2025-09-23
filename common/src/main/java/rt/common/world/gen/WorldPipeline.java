package rt.common.world.gen;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.stages.*;
import java.util.*;

public final class WorldPipeline {
  private final List<ChunkStage> stages;
  private final long seed;

  public WorldPipeline(long seed, List<ChunkStage> stages){
    this.seed = seed;
    this.stages = List.copyOf(stages);
  }

  public void generate(int cx,int cy,int size, byte[] l1, byte[] l2, java.util.BitSet coll){
    TileBuffer buf = new TileBuffer(size, l1, l2, coll);
    Random rng = new Random(seed ^ (cx*73856093L) ^ (cy*19349663L));
    for (ChunkStage s : stages) s.apply(cx, cy, rng, buf);
  }

  /** Pipeline mặc định, thứ tự quan trọng */
  public static WorldPipeline createDefault(WorldGenConfig cfg){
	  return new WorldPipeline(cfg.seed, List.of(
		    new ContinentStage(cfg),   // mask đất/biển
		    new BasePlainStage(cfg),   // trải nền trong lục địa
		    new LakeStage(cfg),        // hồ nội địa
		    new RiverStage(cfg),       // sông uốn lượn + nhánh
		    new CoastStage(5),         // dải cát ven biển (nếu bạn đang dùng)
		    new ForestStage(cfg),      // rừng (tạm)
		    new RareForestStage(cfg, Terrain.F_FOG),       // ~5%
		    new RareForestStage(cfg, Terrain.F_MAGIC),     // ~5%
		    new RareForestStage(cfg, Terrain.F_DARK),      // ~5%
		    new MountainStage(cfg),    // núi cụm/dãy
		    new DesertStage(cfg),      // sa mạc
		    new CollisionStage()       // coll theo Terrain.blocked
		));
  }
}
