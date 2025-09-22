package rt.common.world;

public final class TerrainPalette {
    private TerrainPalette(){}

    public static final int[] ARGB = new int[256];
    static {
        ARGB[Terrain.OCEAN.id]   = 0xFF1162C2; // xanh biển

        ARGB[Terrain.PLAIN.id]   = 0xFF8BC34A; // xanh đồng bằng
        ARGB[Terrain.FOREST.id]  = 0xFF2E7D32; // xanh rừng đậm
        ARGB[Terrain.DESERT.id]  = 0xFFE7C388; // cát vàng

        ARGB[Terrain.LAKE.id]    = 0xFF1E6AA8; // xanh hồ
        ARGB[Terrain.M_ROCK.id]  = 0xFF8C8C8C; // xám núi

        // các id khác: để cùng màu fallback tối giản
        int fallback = 0xFF808080;
        ARGB[Terrain.RIVER.id]       = ARGB[Terrain.LAKE.id];
        ARGB[Terrain.PLAIN_WEIRD.id] = ARGB[Terrain.PLAIN.id];
        ARGB[Terrain.M_SNOW.id]      = ARGB[Terrain.M_ROCK.id];
        ARGB[Terrain.M_VOLCANO.id]   = ARGB[Terrain.M_ROCK.id];
        ARGB[Terrain.M_FOREST.id]    = ARGB[Terrain.M_ROCK.id];
        ARGB[Terrain.MOUNTAIN.id]    = ARGB[Terrain.M_ROCK.id];

        // fill rỗng
        for (int i=0;i<ARGB.length;i++) if (ARGB[i]==0) ARGB[i]=fallback;
    }
    public static int color(int id){ return ARGB[id & 0xFF]; }
}
