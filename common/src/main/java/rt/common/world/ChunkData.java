package rt.common.world;

import java.util.BitSet;

public final class ChunkData {
    public final int cx, cy;           // chunk coordinate
    public final int size;             // e.g., 64
    public final byte[] layer1;        // base tile ids
    public final byte[] layer2;        // overlay tile ids
    public final BitSet collision;     // SOLID bitset

    public ChunkData(int cx, int cy, int size, byte[] l1, byte[] l2, BitSet coll) {
        this.cx = cx; this.cy = cy; this.size = size;
        this.layer1 = l1; this.layer2 = l2; this.collision = coll;
    }
}
