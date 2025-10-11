package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

/** Đánh dấu 1 chunk “tâm làng” trong mỗi ô lưới lớn. Chưa sinh đường/nhà. */
public final class VillageStage implements ChunkStage {
    private final long seed;

    // thông số thưa/dày
    private static final int GRID   = 24; // mỗi 24 chunk xét 1 làng
    private static final int JITTER = 8;  // lệch tâm trong ô (đơn vị chunk)

    // bật kiểm tra “đồng bằng” sau (để tránh phụ thuộc Terrain cụ thể)
    private static final boolean CHECK_PLAINS = false;

    // “index” để sau này mở thêm object (đường/nhà) — hiện giữ <3
    private static final int DETAIL_INDEX = 1; // khi bạn muốn thêm object => đổi 3

    public VillageStage(WorldGenConfig cfg){
        this.seed = cfg.seed ^ 0x7D_E1_5B_A3_9E37_79B9L;
    }

    @Override public void apply(int cx, int cy, Random rng, TileBuffer b){
        int[] center = villageCenter(cx, cy);
        if (center == null) return;
        int ccx = center[0], ccy = center[1];

        // Chỉ chunk đúng tâm làng mới “đánh dấu”
        if (cx != ccx || cy != ccy) return;

        if (CHECK_PLAINS && !isPlainsEnough(b)) return;

        // --- ĐÁNH DẤU NHẸ: một “dấu +” 7x7 tiles ở giữa chunk ---
        final int N = b.size;
        final int cxTile = N/2, cyTile = N/2;
        final int R = 3;

        for (int d = -R; d <= R; d++){
            int x = cxTile + d, y = cyTile;
            if (x>=0 && x<N) b.setL2(x, y, Terrain.VILLAGE_MARK.id);
        }
        for (int d = -R; d <= R; d++){
            int x = cxTile, y = cyTile + d;
            if (y>=0 && y<N) b.setL2(x, y, Terrain.VILLAGE_MARK.id);
        }

        // Sau này khi DETAIL_INDEX >= 3 thì mới thêm đường/nhà/đối tượng:
        if (DETAIL_INDEX >= 3) {
            // TODO: vẽ đường/nhà ở các chunk xung quanh (để trống hiện tại)
        }
    }

    // ===== helpers =====
    private int[] villageCenter(int cx, int cy){
        int gx = Math.floorDiv(cx, GRID), gy = Math.floorDiv(cy, GRID);
        long h = mix(seed, gx, gy);
        // 50% ô có làng (giảm/tăng ngưỡng tuỳ bạn)
        if ((h & 0xFF) > 0x80) return null;

        int offX = (int)((mix(h ^ 0xA5A5_5A5AL, gx, gy) & 0xFFFF) % (2*JITTER+1)) - JITTER;
        int offY = (int)((mix(h ^ 0x5A5A_A5A5L, gx, gy) & 0xFFFF) % (2*JITTER+1)) - JITTER;
        return new int[]{ gx*GRID + offX, gy*GRID + offY };
    }

    private static long mix(long s, long x, long y){
        long h = s;
        h ^= x * 0x9E3779B97F4A7C15L; h = Long.rotateLeft(h, 13);
        h ^= y * 0xC2B2AE3D27D4EB4FL; h = Long.rotateLeft(h, 17);
        h ^= (h>>>33);
        return h;
    }

    /** Tối giản: coi chunk là “đồng bằng đủ” nếu >60% tile hiện là nền (tuỳ chỉnh sau). */
    private static boolean isPlainsEnough(TileBuffer b){
        int N=b.size, ok=0;
        for (int y=0;y<N;y++) for (int x=0;x<N;x++){
            int id = b.getL1(x,y);
            // nếu dự án của bạn có Terrain.PLAIN, thay dòng dưới bằng (id == Terrain.PLAIN.id)
            if (id == 1 || id == 2 || id == 3) ok++; // giả định các id cỏ/đồng bằng
        }
        return ok >= (int)(N*N*0.6);
    }
}
