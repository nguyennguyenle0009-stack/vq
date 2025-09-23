package rt.common.world.gen.stages;

import rt.common.world.ChunkPos;
import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Đặt OCEAN bên ngoài các lục địa. Trong lục địa để PLAIN (nền), các stage sau sẽ phủ lên. */
public final class ContinentStage implements ChunkStage {

    private final long seed;
    private final List<Continent> continents;

    public ContinentStage(WorldGenConfig cfg){
        this.seed = cfg.seed;
        this.continents = buildContinents(cfg.seed);
    }

    @Override public void apply(int cx, int cy, Random rng, TileBuffer b){
        final int N = b.size;
        final long baseX = (long)cx * N;
        final long baseY = (long)cy * N;

        for (int y=0;y<N;y++){
            long gy = baseY + y;
            for (int x=0;x<N;x++){
                long gx = baseX + x;

                boolean land = false;
                for (Continent c : continents){
                    if (c.contains(gx, gy)) { land = true; break; }
                }

                if (!land) {
                    b.setL1(x, y, Terrain.OCEAN.id);
                } else {
                    // trong lục địa: nếu chưa có gì thì set nền PLAIN
                    if (b.getL1(x,y) == 0) b.setL1(x,y, Terrain.PLAIN.id);
                }
            }
        }
    }

    // ===== Continents =====

    private static final class Continent {
        final long cx, cy;     // tâm
        final double r;        // bán kính “tương đương” (ước theo area)
        final long seed;

        Continent(long cx,long cy,double r,long seed){ this.cx=cx; this.cy=cy; this.r=r; this.seed=seed; }

        boolean contains(long x,long y){
            // mặt nạ méo (fbm + warp) để bo tròn + méo tự nhiên
            double dx = x - cx, dy = y - cy;
            double d = Math.sqrt(dx*dx + dy*dy);

            // nhiễu nhẹ theo góc để đường biên không tròn
            double ang = Math.atan2(dy, dx);
            double n = fbmNoise(seed, x*0.00001, y*0.00001, 3);
            double edge = r * (0.90 + 0.12 * n + 0.05 * Math.sin(5*ang + n*3.14));

            return d <= edge;
        }
    }

    /** Sinh danh sách lục địa, đảm bảo khoảng cách rA+rB+500_000. */
    private static List<Continent> buildContinents(long seed){
        Random r = new Random(seed ^ 0x9E3779B97F4A7C15L);
        int count = 10 + r.nextInt(8); // số lục địa “toàn cục” (tuỳ world vô hạn, chỉ những cái gần mới ảnh hưởng)
        ArrayList<Continent> out = new ArrayList<>();
        int tries = 0;
        while (out.size() < count && tries++ < count * 2000){
            // area 1e6..1e7 -> r = sqrt(area/pi)
            double area = 1_000_000d + r.nextDouble() * 9_000_000d;
            double rr = Math.sqrt(area / Math.PI);

            long cx = (long)((r.nextDouble()-0.5) * 4_000_000_000L);
            long cy = (long)((r.nextDouble()-0.5) * 4_000_000_000L);

            boolean ok = true;
            for (Continent c : out){
                double minDist = c.r + rr + 500_000d;
                double dx = cx - c.cx, dy = cy - c.cy;
                if (dx*dx + dy*dy < minDist*minDist) { ok = false; break; }
            }
            if (!ok) continue;

            out.add(new Continent(cx, cy, rr, seed ^ (cx*1315423911L) ^ (cy*2654435761L)));
        }
        return out;
    }

    // ===== tiny noise (deterministic, nhẹ) =====

    private static double hash2(long seed, long ix, long iy){
        long h = seed;
        h ^= ix * 0x9E3779B97F4A7C15L; h = Long.rotateLeft(h, 13);
        h ^= iy * 0xC2B2AE3D27D4EB4FL; h = Long.rotateLeft(h, 17);
        h ^= (h>>>33);
        return ((h & 0xFFFFFFFFFFFFL) / (double)(1L<<48)); // [0,1)
    }
    private static double smooth(double t){ return t*t*(3-2*t); }
    private static double valueNoise(long seed, double x, double y){
        long ix = (long)Math.floor(x), iy = (long)Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double a = hash2(seed, ix,   iy);
        double b = hash2(seed, ix+1, iy);
        double c = hash2(seed, ix,   iy+1);
        double d = hash2(seed, ix+1, iy+1);
        double u = smooth(fx), v = smooth(fy);
        double ab = a + (b-a)*u;
        double cd = c + (d-c)*u;
        return (ab + (cd-ab)*v)*2.0-1.0; // [-1,1]
    }
    private static double fbmNoise(long seed, double x, double y, int oct){
        double amp=1, freq=1, sum=0, norm=0;
        for(int i=0;i<oct;i++){
            sum += amp * valueNoise(seed+i*12345, x*freq, y*freq);
            norm += amp;
            amp *= 0.5; freq *= 2.0;
        }
        return sum / Math.max(1e-9, norm);
    }
}
