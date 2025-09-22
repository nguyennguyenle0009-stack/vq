package rt.client.world;

import rt.client.gfx.TerrainTextures;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class ChunkBaker {
    private ChunkBaker(){}

    /** Gọi trước khi vẽ chunk. Nếu đã bake đúng tileSize thì bỏ qua. */
    public static void bake(ChunkCache.Data d, int tileSize) {
        if (d == null) return;
        if (d.img != null && d.bakedTileSize == tileSize) return;

        final int s = d.size;
        final int W = s * tileSize, H = s * tileSize;

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
        d.img = out;
        d.bakedTileSize = tileSize;
    }
}
