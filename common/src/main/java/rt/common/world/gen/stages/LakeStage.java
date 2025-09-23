package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class LakeStage implements ChunkStage {
  private final long seed;
  public LakeStage(WorldGenConfig cfg){ this.seed = cfg.seed ^ 0x7f31a5b1cafebabeL; }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    final int N=b.size; final long bx=(long)cx*N, by=(long)cy*N;
    for(int y=0;y<N;y++){
      long gy=by+y;
      for(int x=0;x<N;x++){
        if (b.getL1(x,y)==Terrain.OCEAN.id) continue;
        long gx=bx+x;
        double n = fbm(seed, gx*0.0090, gy*0.0090, 3);      // blob lớn
        double n2= fbm(seed^0x55, gx*0.06,  gy*0.06, 2);   // viền răng cưa
        double v = 0.55*n + 0.45*n2;                      // [-1..1]
        if (v > 0.45) b.setL1(x,y, Terrain.LAKE.id);      // ngưỡng -> ~10% nội địa
      }
    }
  }

  // value/fbm noise gọn
  private static double h(long s,long ix,long iy){
    long z=s^ (ix*0x9E3779B97F4A7C15L) ^ (iy*0xC2B2AE3D27D4EB4FL);
    z^=z>>>33; z*=0xff51afd7ed558ccdL; z^=z>>>33; z*=0xc4ceb9fe1a85ec53L; z^=z>>>33;
    return (z>>>11)*(1.0/((1L<<53)-1)); // [0,1)
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
    double a=1,f=1,sum=0,n=0; for(int i=0;i<oct;i++){ sum+=a*vnoise(s+i*9973,x*f,y*f); n+=a; a*=.5; f*=2; }
    return sum/n;
  }
}
