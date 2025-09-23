package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

/** Rừng thường: mảng lớn (blob) 200×200..2000×2000 ~ 20% đất. */
public final class ForestStage implements ChunkStage {
  private final long seed;
  public ForestStage(WorldGenConfig cfg){ this.seed = cfg.seed ^ (long) "FOREST".hashCode();; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    final int N=b.size; final long bx=(long)cx*N, by=(long)cy*N;
    for (int y=0;y<N;y++){
      long gy=by+y;
      for (int x=0;x<N;x++){
        if (b.getL1(x,y)!=Terrain.PLAIN.id) continue; // chỉ phủ trên đồng bằng
        long gx=bx+x;

        // fbm 2 tần số: 0.003~0.008 tạo blob rất lớn, 0.02 để viền bớt đều
        double g1 = fbm(seed,       gx*0.0020, gy*0.0020, 3);
        double g2 = fbm(seed^0x77,  gx*0.020, gy*0.020, 2);
        double v  = 0.75*g1 + 0.25*g2;                 // [-1..1]
        if (v > 0.25) b.setL1(x,y, Terrain.FOREST.id); // ~20% nếu mix như trên
      }
    }
  }

  // ---- noise utils (nhẹ, thuần determinism) ----
  private static double h(long s,long ix,long iy){
    long z=s ^ (ix*0x9E3779B97F4A7C15L) ^ (iy*0xC2B2AE3D27D4EB4FL);
    z^=z>>>33; z*=0xff51afd7ed558ccdL; z^=z>>>33; z*=0xc4ceb9fe1a85ec53L; z^=z>>>33;
    return (z>>>11)*(1.0/((1L<<53)-1)); // [0..1)
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
    double a=1,f=1,sum=0,n=0; for(int i=0;i<oct;i++){ sum+=a*vnoise(s+i*1337,x*f,y*f); n+=a; a*=.5; f*=2; }
    return sum/n;
  }
}
