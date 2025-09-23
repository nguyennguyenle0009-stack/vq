package rt.common.world.gen;
import java.util.BitSet;
public final class TileBuffer {
  public final int size; public final byte[] l1,l2; public final BitSet coll;
  public TileBuffer(int size, byte[] l1, byte[] l2, BitSet coll){
    this.size=size; this.l1=l1; this.l2=l2; this.coll=coll;
  }
  private int idx(int x,int y){ return y*size+x; }
  public int  getL1(int x,int y){ return Byte.toUnsignedInt(l1[idx(x,y)]); }
  public void setL1(int x,int y,int id){ l1[idx(x,y)] = (byte)id; }
  public int  getL2(int x,int y){ return Byte.toUnsignedInt(l2[idx(x,y)]); }
  public void setL2(int x,int y,int id){ l2[idx(x,y)] = (byte)id; }
  public void block(int x,int y, boolean v){ coll.set(idx(x,y), v); }
}
