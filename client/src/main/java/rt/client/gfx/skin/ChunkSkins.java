package rt.client.gfx.skin;

import rt.client.gfx.TerrainTextures;
import rt.common.world.Terrain;

public final class ChunkSkins {
    private ChunkSkins(){}
    public static void init() {
        // Giai đoạn 1: KHÔNG đăng ký gì để giữ màu gốc.
        // Giai đoạn 2 (bật sprite) ví dụ:
        // TerrainTextures.register(Terrain.M_ROCK.id, "nui");
        // TerrainTextures.register(Terrain.PLAIN.id, "plain");
    	TerrainTextures.register(Terrain.PLAIN.id, "plain_bg");
    	TerrainTextures.register(Terrain.LAKE.id, "lake_bg");
    	TerrainTextures.register(Terrain.DESERT.id, "DESERT");
    	TerrainTextures.register(Terrain.FOREST.id, "FOREST");
    }
}
