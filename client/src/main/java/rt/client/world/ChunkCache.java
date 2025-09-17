package rt.client.world;

import java.util.concurrent.*; import java.util.BitSet;

public final class ChunkCache {
  public static final int R = 1; // bán kính 1 → 3×3 chunk quanh center
  private static record Key(int cx,int cy){}
  public static final class Data {
    public final int cx,cy,size; public final byte[] l1,l2; public final BitSet coll;
    public Data(int cx,int cy,int size,byte[] l1,byte[] l2,BitSet coll){
      this.cx=cx; this.cy=cy; this.size=size; this.l1=l1; this.l2=l2; this.coll=coll;}
  }
  private final ConcurrentMap<Key,Data> map = new ConcurrentHashMap<>();
  public void put(int cx,int cy,int size,byte[] l1,byte[] l2,BitSet coll){
    map.put(new Key(cx,cy), new Data(cx,cy,size,l1,l2,coll));
  }
  public Data get(int cx,int cy){ return map.get(new Key(cx,cy)); }
  public void clear(){ map.clear(); }
}
