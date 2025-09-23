package rt.common.world.gen;
import java.util.Random;

public interface ChunkStage {
  void apply(int cx, int cy, Random rng, TileBuffer buf);
}
