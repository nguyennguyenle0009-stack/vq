// rt/client/world/ChunkCache.java
package rt.client.world;

import java.util.*; import java.util.concurrent.*; import java.util.BitSet;

public final class ChunkCache {
  public static final int R = 2;           // tải 5×5 quanh nhân vật (mượt hơn)
  private static final int MAX = 512;      // tối đa giữ 512 chunk (tuỳ RAM)

  private static record Key(int cx,int cy){}

  public static final class Data {
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
}
