package rt.common.world;

import rt.common.world.gen.WorldPipeline;
import rt.common.world.ChunkPos;

import java.util.BitSet;

public final class WorldGenerator {
	  private static volatile WorldPipeline PIPE;

	  // constructor tương thích
	  public WorldGenerator() {}
	  public WorldGenerator(WorldGenConfig cfg) { configure(cfg); }

	  /** Gọi 1 lần ở server sau khi có cfg/seed */
	  public static void configure(WorldGenConfig cfg){
	    PIPE = rt.common.world.gen.WorldPipeline.createDefault(cfg);
	  }

	  /** Build chunk (overload mới) */
	  public static void generateChunk(int cx,int cy,int size, byte[] l1, byte[] l2, BitSet coll){
	    if (PIPE == null) throw new IllegalStateException("WorldGenerator not configured");
	    PIPE.generate(cx, cy, size, l1, l2, coll);
	  }

	  /** Overload cũ (nếu nơi gọi vẫn truyền cfg) */
	  public static void generateChunk(int cx,int cy,int size, byte[] l1, byte[] l2, BitSet coll, WorldGenConfig cfg){
	    if (PIPE == null) configure(cfg);
	    PIPE.generate(cx, cy, size, l1, l2, coll);
	  }

	  /** ✅ HÀM instance cho GeoService: gen.idAt(gx,gy) */
	  public int idAt(int gx, int gy){
	    if (PIPE == null) throw new IllegalStateException("WorldGenerator not configured");

	    final int N = rt.common.world.ChunkPos.SIZE;
	    int cx = Math.floorDiv(gx, N);
	    int cy = Math.floorDiv(gy, N);
	    int tx = Math.floorMod(gx, N);
	    int ty = Math.floorMod(gy, N);

	    byte[] l1 = new byte[N*N];
	    byte[] l2 = new byte[N*N];
	    BitSet coll = new BitSet(N*N);
	    PIPE.generate(cx, cy, N, l1, l2, coll);

	    return Byte.toUnsignedInt(l1[ty * N + tx]); // đúng hành vi cũ: lấy L1
	  }
	}

