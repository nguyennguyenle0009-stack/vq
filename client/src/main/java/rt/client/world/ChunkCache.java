// rt/client/world/ChunkCache.java
package rt.client.world;

import java.util.*; import java.util.concurrent.*; import java.util.BitSet;

public final class ChunkCache {
  public static final int R = 2;           // tải 5×5 quanh nhân vật (mượt hơn)
  private static final int MAX = 512;      // tối đa giữ 512 chunk (tuỳ RAM)

  private static record Key(int cx,int cy){}

  public static final class Data {
	  public volatile java.awt.image.BufferedImage img;   // ảnh baked của chunk
	  public volatile int bakedTileSize = 0;              // tileSize dùng để bake
	  public final int cx,cy,size; public final byte[] l1,l2; public final BitSet coll;
	  public Data(int cx,int cy,int size,byte[] l1,byte[] l2,BitSet coll){
      this.cx=cx; this.cy=cy; this.size=size; this.l1=l1; this.l2=l2; this.coll=coll; }
  }

  // LRU theo access-order
  private final Map<Key,Data> map = Collections.synchronizedMap(
    new LinkedHashMap<>(128,0.75f,true){
      @Override protected boolean removeEldestEntry(Map.Entry<Key,Data> e){ return size()>MAX; }
    });

  // set các chunk đang yêu cầu (tránh gửi trùng)
  private final Set<Key> inflight = ConcurrentHashMap.newKeySet();

  public boolean has(int cx,int cy){ return map.containsKey(new Key(cx,cy)); }
  public Data get(int cx,int cy){ return map.get(new Key(cx,cy)); }
  public void clear(){ map.clear(); inflight.clear(); }

  /** Gắn cờ đã request; trả true nếu vừa gắn (tức là chưa gửi trùng). */
  public boolean markRequested(int cx,int cy){ return inflight.add(new Key(cx,cy)); }
  /** Khi chunk về: xoá cờ in-flight và lưu cache. */
  public void onArrive(Data d){
    inflight.remove(new Key(d.cx,d.cy));
    map.put(new Key(d.cx,d.cy), d);
  }
  
  public void bakeImage(Data d, int tileSize) {
	    if (d.img != null && d.bakedTileSize == tileSize) return;

	    final int N = d.size, W = N * tileSize, H = W;
	    var img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
	    int[] buf = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();

	    // bảng màu tối giản (đổi theo palette của bạn)
	    final int OCEAN=0xFF1E5AA8, PLAIN=0xFFBFDCA6, FOREST=0xFF3D7A3A, DESERT=0xFFE9DFB3, MOUNTAIN=0xFF6E6E6E;
	    final int[] color = { OCEAN, 0, PLAIN, FOREST, DESERT, MOUNTAIN };

	    for (int ty=0; ty<N; ty++) {
	        for (int tx=0; tx<N; tx++) {
	            int id = d.l1[ty*N + tx] & 0xFF;
	            int c  = color[Math.min(id, color.length-1)];
	            // vẽ block tileSize×tileSize vào buffer
	            int x0 = tx*tileSize, y0 = ty*tileSize;
	            for (int sy=0; sy<tileSize; sy++) {
	                int row = (y0+sy) * W + x0;
	                for (int sx=0; sx<tileSize; sx++) buf[row+sx] = c;
	            }
	        }
	    }
	    d.img = img;
	    d.bakedTileSize = tileSize;
	}

}
