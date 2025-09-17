package rt.common.world;

import java.util.BitSet;

/** GĐ1: Biển/Lục địa + Plain/Forest/Desert + điểm núi rải rác (solid).
 *  Xác định (deterministic) theo seed — cùng seed -> kết quả y hệt.
 */
public final class WorldGenerator {
    private final WorldGenConfig cfg;
    public WorldGenerator(WorldGenConfig cfg) { this.cfg = cfg; }

    public ChunkData generate(int cx, int cy) {
        final int N = ChunkPos.SIZE;
        byte[] l1 = new byte[N*N], l2 = new byte[N*N];
        BitSet coll = new BitSet(N*N);

        for (int ty=0; ty<N; ty++) {
            for (int tx=0; tx<N; tx++) {
                long gx = (long)cx*N + tx, gy = (long)cy*N + ty;
                int idx = ty*N + tx;

                // Continental mask: <0.35 là biển (ID=0)
                double cont = noise(cfg.seed*0x9E37L, gx*0xA24BL, gy*0x9FB2L);
                if (cont < 0.35) { l1[idx] = 0; continue; } // OCEAN

                // Biome cấp 3: plain / forest / desert
                double bio = noise(cfg.seed*0x94D0L, gx*0xC2B2L, gy*0x1656L);
                if (bio < cfg.plainRatio)                        l1[idx] = 2; // PLAIN
                else if (bio < cfg.plainRatio + cfg.forestRatio) l1[idx] = 3; // FOREST
                else                                             l1[idx] = 4; // DESERT

                // Điểm núi (solid) – GĐ1 chỉ rải nhẹ để test collision sau này
                double m = noise(cfg.seed*0xD6E8L, gx*0xBF58L, gy*0x94D0L);
                if (m > 0.82) { l1[idx] = 5; coll.set(idx); }   // MOUNTAIN + solid
            }
        }
        return new ChunkData(cx, cy, N, l1, l2, coll);
    }

    private static double noise(long a, long b, long c){ return to01(mix(a,b,c)); }
    private static double to01(long h){ return (h>>>11) / (double)(1L<<53); }
    private static long mix(long a,long b,long c){
        long x = a ^ Long.rotateLeft(b,13) ^ Long.rotateLeft(c,27);
        x^=(x>>>33); x*=0xff51afd7ed558ccdL;
        x^=(x>>>33); x*=0xc4ceb9fe1a85ec53L;
        x^=(x>>>33); return x;
    }
}
