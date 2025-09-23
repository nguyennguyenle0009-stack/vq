package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.WorldGenConfig;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

/** Rừng hiếm: mỗi "macro cell" sinh tối đa 2 cụm lớn cho 1 biome hiếm. */
public final class RareForestStage implements ChunkStage {
  private final long seed;
  private final int terrainId;        // F_FOG / F_MAGIC / F_DARK
  private final double cover;         // mục tiêu ~5%
  public RareForestStage(WorldGenConfig cfg, Terrain t){
    this.seed = cfg.seed ^ (0x51E<<t.id);
    this.terrainId = t.id;
    this.cover = 0.05;
  }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    final int N=b.size; final long bx=(long)cx*N, by=(long)cy*N;

    for (int y=0;y<N;y++){
      long gy=by+y;
      for (int x=0;x<N;x++){
        int cur = b.getL1(x,y);
        // chỉ thay trên FOREST thường để tạo "vùng" hiếm nằm trong rừng
        if (cur != Terrain.FOREST.id) continue;
        long gx=bx+x;

        // noise “island” hiếm — khống chế tần suất bằng ngưỡng cao
        double island = fbm(seed, gx*0.0045, gy*0.0045, 3);  // blob lớn trong rừng
        if (island > 0.70) {
          b.setL1(x,y, terrainId);
        }
      }
    }
  }

  private static double h(long s,long ix,long iy){
    long z=s ^ (ix*0x9E3779B97F4A7C15L) ^ (iy*0xC2B2AE3D27D4EB4FL);
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
    double a=1,f=1,sum=0,n=0; for(int i=0;i<oct;i++){ sum+=a*vnoise(s+i*9001,x*f,y*f); n+=a; a*=.5; f*=1.9; }
    return sum/n;
  }
}
