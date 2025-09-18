package rt.server.world.chunk;

import rt.common.util.DesktopDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Lưu/đọc delta chunk ra đĩa (tương tự cơ chế .mca). */
public final class ChunkStorage {
    private static final int MAGIC = 0x56433130; // "VC10"
    private final Path dir;

    public ChunkStorage(Path dir) {
        try {
            this.dir = dir;
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("create chunk dir", e);
        }
    }

    public static ChunkStorage createDefault() {
        Path base = DesktopDir.resolve().resolve("Vương quyền").resolve("server").resolve("chunks");
        return new ChunkStorage(base);
    }

    private Path file(int cx, int cy) {
        return dir.resolve("chunk_%d_%d.bin".formatted(cx, cy));
    }

    public ChunkDelta load(int cx, int cy) {
        Path path = file(cx, cy);
        if (!Files.exists(path)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int magic = in.readInt();
            if (magic != MAGIC) return null;
            int size = in.readInt();
            ChunkDelta delta = new ChunkDelta(size);

            int n1 = in.readInt();
            for (int i = 0; i < n1; i++) {
                int idx = in.readInt();
                byte val = in.readByte();
                delta.setLayer1(idx, val);
            }
            int n2 = in.readInt();
            for (int i = 0; i < n2; i++) {
                int idx = in.readInt();
                byte val = in.readByte();
                delta.setLayer2(idx, val);
            }
            int s = in.readInt();
            for (int i = 0; i < s; i++) delta.setCollision(in.readInt(), true);
            int c = in.readInt();
            for (int i = 0; i < c; i++) delta.setCollision(in.readInt(), false);
            return delta;
        } catch (IOException e) {
            throw new UncheckedIOException("load chunk delta", e);
        }
    }

    public void save(int cx, int cy, ChunkDelta delta) {
        Path path = file(cx, cy);
        if (delta == null || delta.isEmpty()) {
            try { Files.deleteIfExists(path); } catch (IOException ignore) {}
            return;
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC);
            out.writeInt(delta.size());

            out.writeInt(delta.layer1().size());
            for (Map.Entry<Integer, Byte> e : delta.layer1().entrySet()) {
                out.writeInt(e.getKey());
                out.writeByte(e.getValue());
            }

            out.writeInt(delta.layer2().size());
            for (Map.Entry<Integer, Byte> e : delta.layer2().entrySet()) {
                out.writeInt(e.getKey());
                out.writeByte(e.getValue());
            }

            out.writeInt(delta.solid().cardinality());
            for (int idx = delta.solid().nextSetBit(0); idx >= 0; idx = delta.solid().nextSetBit(idx + 1)) {
                out.writeInt(idx);
            }

            out.writeInt(delta.clearedSolid().cardinality());
            for (int idx = delta.clearedSolid().nextSetBit(0); idx >= 0; idx = delta.clearedSolid().nextSetBit(idx + 1)) {
                out.writeInt(idx);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("save chunk delta", e);
        }
    }

    public void update(int cx, int cy, ChunkDelta changes) {
        if (changes == null || changes.isEmpty()) return;
        ChunkDelta existing = loadSafe(cx, cy, changes.size());
        existing.absorb(changes);
        save(cx, cy, existing);
    }

    private ChunkDelta loadSafe(int cx, int cy, int size) {
        ChunkDelta delta = load(cx, cy);
        if (delta == null) delta = new ChunkDelta(size);
        return delta;
    }
}
