package rt.client.world;

import rt.common.world.ChunkPos;
import rt.common.world.WorldGenerator;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ nhớ đệm chunk ở client. Thay vì nhận dữ liệu từ server,
 * client tự sinh chunk bằng {@link WorldGenerator} dựa trên seed chung.
 */
public final class ChunkCache {
    public static final int R = 2;                  // bán kính tải
    private static final int MAX = 512;             // tối đa số chunk giữ lại

    private static record Key(int cx, int cy) {}

    public static final class Data {
        public final int cx, cy, size;
        public final byte[] l1, l2;
        public final BitSet coll;

        // Ảnh đã bake theo từng tileSize
        public final ConcurrentHashMap<Integer, java.awt.image.BufferedImage> img = new ConcurrentHashMap<>();

        public Data(int cx, int cy, int size, byte[] l1, byte[] l2, BitSet coll) {
            this.cx = cx;
            this.cy = cy;
            this.size = size;
            this.l1 = l1;
            this.l2 = l2;
            this.coll = coll;
        }
    }

    private final Map<Key, Data> map = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Data> eldest) {
                    return size() > MAX;
                }
            }
    );

    public void clear() {
        map.clear();
    }

    /**
     * Lấy chunk từ cache; nếu chưa có và world generator đã được cấu hình,
     * sinh chunk mới, lưu vào cache rồi trả về.
     */
    public Data get(int cx, int cy) {
        Key key = new Key(cx, cy);
        Data existing = map.get(key);
        if (existing != null) return existing;

        synchronized (map) {
            existing = map.get(key);
            if (existing != null) return existing;

            final int size = ChunkPos.SIZE;
            byte[] l1 = new byte[size * size];
            byte[] l2 = new byte[size * size];
            BitSet coll = new BitSet(size * size);
            try {
                WorldGenerator.generateChunk(cx, cy, size, l1, l2, coll);
            } catch (IllegalStateException ex) {
                return null; // seed chưa được thiết lập
            }

            Data data = new Data(cx, cy, size, l1, l2, coll);
            map.put(key, data);
            return data;
        }
    }
}
