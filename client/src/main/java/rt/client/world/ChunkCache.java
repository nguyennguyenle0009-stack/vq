package rt.client.world;

import java.awt.image.BufferedImage;
import java.util.*; import java.util.concurrent.*; import java.util.BitSet;

public final class ChunkCache {
  public static final int R = 2;           // bán kính tải
  private static final int MAX = 512;      // tối đa cache chunk

  private static record Key(int cx,int cy){}

//  public static final class Data {
//    //public volatile java.awt.image.BufferedImage img;   // ảnh baked của chunk
//    public volatile java.util.concurrent.ConcurrentHashMap<Integer, BufferedImage> img = new ConcurrentHashMap<>();
//    public volatile int bakedTileSize = 0;              // tileSize dùng để bake
//    public final int cx,cy,size; public final byte[] l1,l2; public final BitSet coll;
//    public Data(int cx,int cy,int size,byte[] l1,byte[] l2,BitSet coll){
//      this.cx=cx; this.cy=cy; this.size=size; this.l1=l1; this.l2=l2; this.coll=coll;
//    }
//  }
  public static final class Data {
	  // ... các field khác giữ nguyên
	  public final int cx, cy, size;
	  public final byte[] l1, l2;
	  public final java.util.BitSet coll;

	  public volatile int bakedTileSize = 0; 
	  
	  // NHIỀU ẢNH theo tileSize
	  public final java.util.concurrent.ConcurrentHashMap<Integer, java.awt.image.BufferedImage> img
	      = new java.util.concurrent.ConcurrentHashMap<>();

	  public Data(int cx,int cy,int size,byte[] l1,byte[] l2,java.util.BitSet coll){
	    this.cx=cx; this.cy=cy; this.size=size; this.l1=l1; this.l2=l2; this.coll=coll;
	  }
	}


  private final Map<Key,Data> map = Collections.synchronizedMap(
    new LinkedHashMap<>(128,0.75f,true){
      @Override protected boolean removeEldestEntry(Map.Entry<Key,Data> e){ return size()>MAX; }
    });

  private final Set<Key> inflight = ConcurrentHashMap.newKeySet();

  public boolean has(int cx,int cy){ return map.containsKey(new Key(cx,cy)); }
  public Data get(int cx,int cy){ return map.get(new Key(cx,cy)); }
  public void clear(){ map.clear(); inflight.clear(); }

  public boolean markRequested(int cx,int cy){ return inflight.add(new Key(cx,cy)); }
  public void onArrive(Data d){
    inflight.remove(new Key(d.cx,d.cy));
    map.put(new Key(d.cx,d.cy), d);
  }

  /**
   * Trả về chunk nếu đã có trong cache, nếu chưa thì tự sinh cục bộ bằng WorldGenerator.
   */
  public ChunkCache.Data getOrGenerateLocal(int cx, int cy) {
	    Data d = get(cx, cy);
	    if (d != null) return d;

	    final int N = rt.common.world.ChunkPos.SIZE;
	    byte[] l1 = new byte[N*N], l2 = new byte[N*N];
	    java.util.BitSet coll = new java.util.BitSet(N*N);
	    rt.common.world.WorldGenerator.generateChunk(cx, cy, N, l1, l2, coll);

	    d = new Data(cx, cy, N, l1, l2, coll);
	    map.put(new Key(cx, cy), d);
	    return d;
	}



}
