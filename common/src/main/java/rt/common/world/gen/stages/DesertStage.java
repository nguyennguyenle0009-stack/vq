package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sa mạc mảng lớn (oval/bean), 1–3 vùng mỗi “khu vực” thế giới, ~5% đất. */
public final class DesertStage implements ChunkStage {
  private final long seed;
  public DesertStage(WorldGenConfig cfg){ this.seed = cfg.seed ^ 0xDE57AABBCCDDL; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    final int N=b.size; final long bx=(long)cx*N, by=(long)cy*N;
    List<Oval> ovals = cachedOvals();
    for(int y=0;y<N;y++){
      long gy=by+y;
      for(int x=0;x<N;x++){
        if (b.getL1(x,y)==Terrain.OCEAN.id) continue;
        long gx=bx+x;
        for (Oval o : ovals){
          if (o.contains(gx,gy)) { b.setL1(x,y, Terrain.DESERT.id); break; }
        }
      }
    }
  }

  // tạo 1–3 oval lớn cho khu vực, deterministic theo seed (không theo lục địa để đơn giản)
  private volatile List<Oval> cache;
  private List<Oval> cachedOvals(){
    if (cache!=null) return cache;
    synchronized (this){
      if (cache!=null) return cache;
      Random r = new Random(seed^0x11223344556677L);
      int c = 1 + r.nextInt(3);
      ArrayList<Oval> list = new ArrayList<>(c);
      for(int i=0;i<c;i++){
        long cx = (long)((r.nextDouble()-0.5)*2_000_000_000L);
        long cy = (long)((r.nextDouble()-0.5)*2_000_000_000L);
        double rx = 600 + r.nextDouble()*1600;   // bán kính lớn
        double ry = 400 + r.nextDouble()*1200;
        double rot= r.nextDouble()*Math.PI;
        list.add(new Oval(cx,cy,rx,ry,rot, seed^(i*777)));
      }
      cache=list; return list;
    }
  }

  private static final class Oval{
    final long cx,cy; final double rx,ry,cos,sin; final long s;
    Oval(long cx,long cy,double rx,double ry,double rot,long s){
      this.cx=cx; this.cy=cy; this.rx=rx; this.ry=ry; this.cos=Math.cos(rot); this.sin=Math.sin(rot); this.s=s;
    }
    boolean contains(long x,long y){
      double dx=x-cx, dy=y-cy;
      double ux= (dx*cos+dy*sin)/rx,  uy = (-dx*sin+dy*cos)/ry;
      double d = ux*ux + uy*uy; // ~1 ở rìa
      // ripple noise làm viền méo tự nhiên
      double w = 0.06 * fbm(s, x*0.01, y*0.01, 2);
      return d <= 1.0 + w;
    }
  }

  private static double h(long s,long ix,long iy){
    long z=s^ (ix*0x9E3779B97F4A7C15L) ^ (iy*0xC2B2AE3D27D4EB4FL);
    z^=z>>>33; z*=0xff51afd7ed558ccdL; z^=z>>>33; z*=0xc4ceb9fe1a85ec53L; z^=z>>>33;
    return (z>>>11)*(1.0/((1L<<53)-1));
  }
  private static double s(double t){ return t*t*(3-2*t); }
  private static double vnoise(long s,double x,double y){
    long ix=(long)Math.floor(x), iy=(long)Math.floor(y);
    double fx=x-ix, fy=y-iy;
    double a=h(s,ix,iy), b=h(s,ix+1,iy), c=h(s,ix,iy+1), d=h(s,ix+1,iy+1);
    double u=s(fx), v=s(fy); double ab=a+(b-a)*u, cd=c+(d-c)*u;
    return (ab+(cd-ab)*v)*2-1;
  }
  private static double fbm(long s,double x,double y,int oct){
    double a=1,f=1,sum=0,n=0; for(int i=0;i<oct;i++){ sum+=a*vnoise(s+i*8191,x*f,y*f); n+=a; a*=.5; f*=2; }
    return sum/n;
  }
}
