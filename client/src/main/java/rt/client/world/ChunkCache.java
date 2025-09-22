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

//  public void bakeImage(Data d, int tileSize) {
//    if (d.img != null && d.bakedTileSize == tileSize) return;
//
//    final int N = d.size, W = N * tileSize, H = W;
//    var img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
//    int[] buf = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
//
//    // ===== Dùng chung palette với bản đồ =====
//    final rt.common.world.TerrainPalette palette = null; // chỉ để rõ import
//    for (int ty=0; ty<N; ty++) {
//      int y0 = ty * tileSize;
//      for (int tx=0; tx<N; tx++) {
//        int id = d.l1[ty*N + tx] & 0xFF;
//        int c  = rt.common.world.TerrainPalette.color(id);
//
//        int x0 = tx * tileSize;
//        for (int sy=0; sy<tileSize; sy++) {
//          int row = (y0+sy) * W + x0;
//          for (int sx=0; sx<tileSize; sx++) buf[row + sx] = c;
//        }
//      }
//    }
//    d.img = img;
//    d.bakedTileSize = tileSize;
//  }
}
