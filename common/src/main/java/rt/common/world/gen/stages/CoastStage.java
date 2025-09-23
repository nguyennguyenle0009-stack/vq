package rt.common.world.gen.stages;

import rt.common.world.Terrain;
import rt.common.world.gen.ChunkStage;
import rt.common.world.gen.TileBuffer;

import java.util.Random;

public final class CoastStage implements ChunkStage {
  private final int sandWidth;
  public CoastStage(int sandWidth){ this.sandWidth = Math.max(1, sandWidth); }

  @Override public void apply(int cx,int cy, Random rng, TileBuffer b){
    // TODO: thay bằng logic thật của bạn
    for (int y=0;y<b.size;y++) for (int x=0;x<b.size;x++){
      int t = b.getL1(x,y);
      if (t!=Terrain.OCEAN.id) {
        // nếu có OCEAN trong phạm vi sandWidth -> chuyển thành "cát" tạm dùng DESERT
        boolean nearOcean=false;
        for (int dy=-sandWidth; dy<=sandWidth && !nearOcean; dy++)
          for (int dx=-sandWidth; dx<=sandWidth && !nearOcean; dx++){
            int xx=x+dx, yy=y+dy;
            if (xx<0||yy<0||xx>=b.size||yy>=b.size) continue;
            nearOcean = (b.getL1(xx,yy)==Terrain.OCEAN.id);
          }
        if (nearOcean) b.setL1(x,y, Terrain.DESERT.id);
      }
    }
  }
}
