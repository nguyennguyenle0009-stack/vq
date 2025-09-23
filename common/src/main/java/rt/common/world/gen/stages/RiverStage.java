package rt.common.world.gen.stages;

import rt.common.world.ChunkPos;
import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sông uốn lượn, độ rộng thay đổi, có nhánh; nằm trong nội địa. */
public final class RiverStage implements ChunkStage {

    private final long seed;

    // tham số hợp lý theo chốt
    private final int mainPerContinentMin = 1, mainPerContinentMax = 3;
    private final int tributaryMin = 2, tributaryMax = 6;

    public RiverStage(WorldGenConfig cfg){
        this.seed = cfg.seed ^ 0xA5A5A5A5DEADBEEFL;
    }

    @Override public void apply(int cx, int cy, Random rng, TileBuffer b){
        final int N = b.size;
        final long baseX = (long)cx * N;
        final long baseY = (long)cy * N;

        // Xác định lục địa gần nhất để bám theo (từ ContinentStage logic)
        ContRef cont = findNearestContinent(baseX + N/2, baseY + N/2);
        if (cont == null) return; // vùng biển, bỏ

        // Polyline sông chính của lục địa (deterministic)
        List<River> rivers = riversForContinent(cont);

        // Rasterize từng sông (kênh nước)
        for (River r : rivers){
            drawRiverSegmentToChunk(r, b, baseX, baseY);
            for (River tr : r.tribs) drawRiverSegmentToChunk(tr, b, baseX, baseY);
        }
    }

    // ===== River representation =====

    private static final class River {
        final List<Point> pts;       // polyline (world tile)
        final double minW, maxW;     // width in tiles
        final List<River> tribs = new ArrayList<>();
        River(List<Point> pts,double minW,double maxW){ this.pts=pts; this.minW=minW; this.maxW=maxW; }
    }
    private record Point(long x,long y){}

    // ===== Build rivers for a continent =====

    private static final class ContRef { final long cx, cy; final double r; final long seed; ContRef(long cx,long cy,double r,long seed){this.cx=cx;this.cy=cy;this.r=r;this.seed=seed;} }

    private ContRef findNearestContinent(long x,long y){
        // phải trùng cách ContinentStage xây danh sách; tạo lại nhanh theo seed + cùng trình tự
        List<ContRef> list = cachedContinents();
        ContRef best = null; double bd = Double.POSITIVE_INFINITY;
        for (ContRef c : list){
            double dx=x-c.cx, dy=y-c.cy, d=dx*dx+dy*dy;
            if (d<bd){ bd=d; best=c; }
        }
        return best;
    }

    // cache continents nhẹ (cùng seed với ContinentStage)
    private volatile List<ContRef> contCache;
    private List<ContRef> cachedContinents(){
        List<ContRef> cc = contCache;
        if (cc != null) return cc;
        synchronized (this){
            if (contCache != null) return contCache;
            Random r = new Random(seed ^ 0x9E3779B97F4A7C15L);
            int count = 10 + r.nextInt(8);
            ArrayList<ContRef> out = new ArrayList<>();
            int tries = 0;
            while (out.size() < count && tries++ < count * 2000){
                double area = 1_000_000d + r.nextDouble() * 9_000_000d;
                double rr = Math.sqrt(area / Math.PI);
                long cx = (long)((r.nextDouble()-0.5) * 4_000_000_000L);
                long cy = (long)((r.nextDouble()-0.5) * 4_000_000_000L);
                boolean ok = true;
                for (ContRef c : out){
                    double minDist = c.r + rr + 500_000d;
                    double dx=cx-c.cx, dy=cy-c.cy;
                    if (dx*dx + dy*dy < minDist*minDist) { ok=false; break; }
                }
                if (!ok) continue;
                out.add(new ContRef(cx,cy,rr, seed ^ (cx*1315423911L) ^ (cy*2654435761L)));
            }
            contCache = out;
            return out;
        }
    }

    private List<River> riversForContinent(ContRef c){
        Random r = new Random(c.seed ^ 0x5F3564951A2BL);
        int count = mainPerContinentMin + r.nextInt(mainPerContinentMax - mainPerContinentMin + 1);

        ArrayList<River> out = new ArrayList<>(count);
        for (int i=0;i<count;i++){
            // trục chính: chọn hai cực trong nội địa, tránh mép r (200 tiles)
            double margin = Math.max(200.0, c.r*0.10);
            double len = (c.r*1.2) + r.nextDouble()* (c.r*1.0);
            double ang = r.nextDouble()*Math.PI*2;

            long x0 = (long)(c.cx + Math.cos(ang)*Math.max(margin, c.r*0.3));
            long y0 = (long)(c.cy + Math.sin(ang)*Math.max(margin, c.r*0.3));
            long x1 = (long)(x0 + Math.cos(ang)*len);
            long y1 = (long)(y0 + Math.sin(ang)*len);

            River main = buildMeanderRiver(c.seed ^ (i*911), x0,y0, x1,y1,
                    2 + r.nextInt(3),  // minW 2–4
                    8 + r.nextInt(7),  // maxW 8–14
                    60 + r.nextInt(120),    // amp
                    (1e-3) + r.nextDouble()* (3e-3), // freq
                    6 + r.nextInt(5));      // step

            // tributaries
            int tcount = tributaryMin + r.nextInt(tributaryMax-tributaryMin+1);
            for (int t=0;t<tcount;t++){
                long tx0 = (long)(c.cx + (r.nextDouble()-0.5)*c.r*1.4);
                long ty0 = (long)(c.cy + (r.nextDouble()-0.5)*c.r*1.4);
                River tr = buildMeanderRiver(c.seed ^ (i*131 + t*733), tx0,ty0, x0,y0,
                        Math.max(1, (int)Math.round(main.minW*0.4)),
                        Math.max(2, (int)Math.round(main.maxW*0.6)),
                        40 + r.nextInt(80),
                        (1e-3) + r.nextDouble()* (3e-3),
                        6 + r.nextInt(5));
                main.tribs.add(tr);
            }
            out.add(main);
        }
        return out;
    }

    private River buildMeanderRiver(long s, long x0,long y0, long x1,long y1,
                                    int minW, int maxW,
                                    double amp, double freq, int step){
        ArrayList<Point> poly = new ArrayList<>();
        double dx = x1-x0, dy=y1-y0;
        double len = Math.max(1.0, Math.hypot(dx,dy));
        double nx = -dy/len, ny = dx/len; // pháp tuyến
        int samples = (int)Math.max(2, Math.round(len/ Math.max(1, step)));

        for (int i=0;i<=samples;i++){
            double t = (double)i/samples;          // 0..1
            double px = x0 + dx*t;
            double py = y0 + dy*t;

            // meander = lệch theo pháp tuyến bởi noise
            double n = fbmNoise(s, (px+10000)*freq, (py-20000)*freq, 3); // [-1,1]
            double off = amp * n;
            long rx = Math.round(px + nx*off);
            long ry = Math.round(py + ny*off);

            poly.add(new Point(rx, ry));
        }
        return new River(poly, minW, maxW);
    }

    // Vẽ “capsule” quanh các đoạn polyline vào chunk
    private void drawRiverSegmentToChunk(River r, TileBuffer b, long baseX, long baseY){
        final int N = b.size;
        // quét từng tile của chunk (đơn giản – dễ hiểu, vẫn nhanh vì chỉ khi chunk sinh)
        for (int y=0;y<N;y++){
            long gy = baseY + y;
            for (int x=0;x<N;x++){
                long gx = baseX + x;

                // khoảng cách tối thiểu đến polyline
                double dist = distToPolyline(gx, gy, r.pts);
                // width biến thiên nhẹ theo vị trí dọc sông
                double w = widthAt(r, gx, gy);
                if (dist <= w*0.5){
                    b.setL1(x, y, Terrain.RIVER.id); // kênh nước = blocked
                } else {
                    // floodplain/bờ (không blocked) – nếu cần, bạn có thể set layer-2 hoặc autotile
                    // (để đơn giản: bỏ qua, chỉ vẽ kênh)
                }
            }
        }
    }

    private double widthAt(River r, long gx, long gy){
        double n = fbmNoise(seed ^ 0xABCD1234L, gx*0.0025, gy*0.0025, 2)*0.5+0.5; // [0,1]
        return r.minW + (r.maxW - r.minW) * n;
    }

    private static double distToPolyline(long x,long y, List<Point> pts){
        double best = Double.POSITIVE_INFINITY;
        for (int i=1;i<pts.size();i++){
            Point a = pts.get(i-1), b = pts.get(i);
            best = Math.min(best, distPointToSegment(x,y, a.x(),a.y(), b.x(),b.y()));
            if (best < 0.5) break;
        }
        return best;
    }

    private static double distPointToSegment(long px,long py, long x1,long y1, long x2,long y2){
        double vx = x2-x1, vy=y2-y1;
        double wx = px-x1, wy=py-y1;
        double c1 = vx*wx + vy*wy;
        if (c1 <= 0) return Math.hypot(px-x1, py-y1);
        double c2 = vx*vx + vy*vy;
        if (c2 <= c1) return Math.hypot(px-x2, py-y2);
        double t = c1 / c2;
        double bx = x1 + t*vx, by = y1 + t*vy;
        return Math.hypot(px-bx, py-by);
    }

    // ===== noise nhỏ =====

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
