package rt.client.world.map;

import rt.common.world.ChunkData;
import rt.common.world.ChunkPos;
import rt.common.world.WorldGenConfig;
import rt.common.world.WorldGenerator;

import java.awt.image.BufferedImage;

public final class MapRenderer {
    private final WorldGenerator gen;
    public MapRenderer(WorldGenConfig cfg) { this.gen = new WorldGenerator(cfg); }

    // tilesPerPixel: số tile trên 1 pixel (zoom). originX/Y: tile góc trái-trên khung vẽ.
    public BufferedImage render(long originX, long originY, double tilesPerPixel, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final int N = ChunkPos.SIZE;

        for (int py = 0; py < h; py++) {
            long gy = originY + (long)Math.floor(py * tilesPerPixel);
            int cgy = (int)Math.floorDiv(gy, N);
            int ty  = Math.floorMod((int)gy, N);

            for (int px = 0; px < w; px++) {
                long gx = originX + (long)Math.floor(px * tilesPerPixel);
                int cgx = (int)Math.floorDiv(gx, N);
                int tx  = Math.floorMod((int)gx, N);

                ChunkData cd = gen.generate(cgx, cgy);
                int id = cd.layer1[ty * N + tx] & 0xff;
                img.setRGB(px, py, palette(id));
            }
        }
        return img;
    }

    private static int palette(int id){
        // placeholder màu: có thể thay bằng atlas sau
        switch (rt.common.world.Terrain.byId(id)) {
            case OCEAN:    return 0xFF1E90FF; // biển
            case PLAIN:    return 0xFF9ACD32; // đồng bằng
            case FOREST:   return 0xFF2E8B57; // rừng
            case DESERT:   return 0xFFDEB887; // sa mạc
            case MOUNTAIN: return 0xFF8B8B83; // núi
            default:       return 0xFF808080;
        }
    }
}
