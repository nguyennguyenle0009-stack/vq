package rt.server.world.chunk;

import rt.common.world.ChunkData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** Lưu trữ phần chênh lệch (delta) của một chunk so với world-gen gốc. */
public final class ChunkDelta {
    private final int size;
    private final Map<Integer, Byte> layer1 = new HashMap<>();
    private final Map<Integer, Byte> layer2 = new HashMap<>();
    private final BitSet solid = new BitSet();
    private final BitSet cleared = new BitSet();

    public ChunkDelta(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be >0");
        this.size = size;
    }

    public int size() { return size; }
    public Map<Integer, Byte> layer1() { return layer1; }
    public Map<Integer, Byte> layer2() { return layer2; }
    public BitSet solid() { return solid; }
    public BitSet clearedSolid() { return cleared; }

    private void ensureIndex(int idx) {
        if (idx < 0 || idx >= size * size) {
            throw new IndexOutOfBoundsException("idx=" + idx + " size=" + size);
        }
    }

    public void setLayer1(int idx, byte value) {
        ensureIndex(idx);
        layer1.put(idx, value);
    }

    public void setLayer2(int idx, byte value) {
        ensureIndex(idx);
        layer2.put(idx, value);
    }

    public void setCollision(int idx, boolean block) {
        ensureIndex(idx);
        if (block) {
            solid.set(idx);
            cleared.clear(idx);
        } else {
            cleared.set(idx);
            solid.clear(idx);
        }
    }

    public boolean isEmpty() {
        return layer1.isEmpty() && layer2.isEmpty() && solid.isEmpty() && cleared.isEmpty();
    }

    public void apply(ChunkData data) {
        if (data.size != size) throw new IllegalArgumentException("chunk size mismatch: " + data.size + " vs " + size);
        layer1.forEach((idx, val) -> data.layer1[idx] = val);
        layer2.forEach((idx, val) -> data.layer2[idx] = val);
        for (int idx = solid.nextSetBit(0); idx >= 0; idx = solid.nextSetBit(idx + 1)) {
            data.collision.set(idx);
        }
        for (int idx = cleared.nextSetBit(0); idx >= 0; idx = cleared.nextSetBit(idx + 1)) {
            data.collision.clear(idx);
        }
    }

    public void absorb(ChunkDelta other) {
        if (other == null || other.isEmpty()) return;
        if (other.size != this.size) throw new IllegalArgumentException("size mismatch");
        other.layer1.forEach(this::setLayer1);
        other.layer2.forEach(this::setLayer2);
        for (int idx = other.solid.nextSetBit(0); idx >= 0; idx = other.solid.nextSetBit(idx + 1)) {
            setCollision(idx, true);
        }
        for (int idx = other.cleared.nextSetBit(0); idx >= 0; idx = other.cleared.nextSetBit(idx + 1)) {
            setCollision(idx, false);
        }
    }
}
