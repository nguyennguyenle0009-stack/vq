// rt/client/world/map/MapRenderer.java
package rt.client.world.map;

import rt.common.world.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class MapRenderer {
    private final WorldGenerator gen;
    private static final int[] PALETTE = new int[256];
    static {
        PALETTE[Terrain.OCEAN.id]    = 0xFF1E90FF;
        PALETTE[Terrain.PLAIN.id]    = 0xFF9ACD32;
        PALETTE[Terrain.FOREST.id]   = 0xFF2E8B57;
        PALETTE[Terrain.DESERT.id]   = 0xFFDEB887;
        PALETTE[Terrain.MOUNTAIN.id] = 0xFF8B8B83;
    }

    public MapRenderer(WorldGenConfig cfg){ this.gen = new WorldGenerator(cfg); }

    public BufferedImage render(long originX, long originY, double tilesPerPixel, int w, int h){
    	if (w <= 0 || h <= 0) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        final int N = ChunkPos.SIZE;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Map<Long, ChunkData> cache = new HashMap<>(256);

        for (int py=0; py<h; py++){
            long gy = originY + (long)Math.floor(py * tilesPerPixel);
            int cgy = Math.floorDiv((int)gy, N);
            int ty  = Math.floorMod((int)gy, N);

            int lastCgx = Integer.MIN_VALUE;
            ChunkData cd = null;

            for (int px=0; px<w; px++){
                long gx = originX + (long)Math.floor(px * tilesPerPixel);
                int cgx = Math.floorDiv((int)gx, N);
                int tx  = Math.floorMod((int)gx, N);

                if (cgx != lastCgx) {
                    long key = (((long)cgx) << 32) ^ (cgy & 0xffffffffL);
                    cd = cache.get(key);
                    if (cd == null) { cd = gen.generate(cgx, cgy); cache.put(key, cd); }
                    lastCgx = cgx;
                }
                
                int id = cd.layer1[ty * N + tx] & 0xff;
                img.setRGB(px, py, TerrainPalette.color(id));

//                int id = cd.layer1[ty * N + tx] & 0xff;
//                img.setRGB(px, py, PALETTE[id] != 0 ? PALETTE[id] : 0xFF808080);
            }
        }
        return img;
    }
}
