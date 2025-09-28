package rt.client.world;

import rt.client.gfx.TerrainTextures;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class ChunkBaker {
	  private ChunkBaker(){}

	  /** Bake nếu thiếu và TRẢ VỀ ảnh cho tileSize yêu cầu. */
	  public static BufferedImage getImage(ChunkCache.Data d, int tileSize) {
		  BufferedImage img = d.img.get(tileSize);
		  if (img != null) return img;

		  final int s  = d.size;
		  final int W  = s * tileSize;
		  final int H  = s * tileSize;

		  BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		  Graphics2D g = out.createGraphics();
		  g.setComposite(AlphaComposite.SrcOver);

		  int idx = 0;
		  for (int y = 0; y < s; y++) {
		    for (int x = 0; x < s; x++, idx++) {
		      int t1 = Byte.toUnsignedInt(d.l1[idx]);
		      if (t1 != 0) {
		        g.drawImage(TerrainTextures.getTile(t1, tileSize, x, y), x * tileSize, y * tileSize, null);
		      }
		      int t2 = Byte.toUnsignedInt(d.l2[idx]);
		      if (t2 != 0) {
		        g.drawImage(TerrainTextures.getTile(t2, tileSize, x, y), x * tileSize, y * tileSize, null);
		      }
		    }
		  }
		  g.dispose();

		  d.img.put(tileSize, out);
		  return out;
		}
	  
	  public static BufferedImage getImageTemp(ChunkCache.Data d, int tileSize) {
		    final int s = d.size, W = s * tileSize, H = s * tileSize;
		    BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		    Graphics2D g = out.createGraphics();
		    g.setComposite(AlphaComposite.SrcOver);

		    int idx = 0;
		    for (int y = 0; y < s; y++) {
		        for (int x = 0; x < s; x++, idx++) {
		            int t1 = Byte.toUnsignedInt(d.l1[idx]);
		            if (t1 != 0) g.drawImage(TerrainTextures.getTile(t1, tileSize, x, y),
		                                     x * tileSize, y * tileSize, null);
		            int t2 = Byte.toUnsignedInt(d.l2[idx]);
		            if (t2 != 0) g.drawImage(TerrainTextures.getTile(t2, tileSize, x, y),
		                                     x * tileSize, y * tileSize, null);
		        }
		    }
		    g.dispose();
		    return out; // KHÔNG put vào d.img ⇒ vẽ xong GC thu hồi, không phình heap
		}


	  /** Tiện ích: lấy “ảnh gần nhất” nếu chưa có đúng tileSize. */
	  public static java.awt.image.BufferedImage getNearestImage(ChunkCache.Data d, int tileSize) {
	    java.awt.image.BufferedImage exact = d.img.get(tileSize);
	    if (exact != null) return exact;
	    // chọn key gần nhất nếu đã có; nếu chưa có gì thì bake đúng size
	    Integer best = null;
	    for (Integer k : d.img.keySet()) {
	      if (best == null || Math.abs(k - tileSize) < Math.abs(best - tileSize)) best = k;
	    }
	    if (best != null) return d.img.get(best);
	    return getImage(d, tileSize);
	  }
	}
