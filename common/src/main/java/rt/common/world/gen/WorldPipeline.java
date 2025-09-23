package rt.common.world.gen;

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
      new BasePlainStage(cfg),      // lấp đồng bằng làm nền
      new LakeStage(cfg),           // hồ trong đất liền
      new RiverStage(cfg),          // sông
      new CoastStage(5),            // dải cát bờ biển 5 ô
      new ForestStage(cfg),         // rừng các biến thể
      new MountainStage(cfg),       // núi/đá
      new DesertStage(cfg),          // sa mạc
      new CollisionStage()
    ));
  }
}
