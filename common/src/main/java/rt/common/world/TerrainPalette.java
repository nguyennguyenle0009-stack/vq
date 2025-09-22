package rt.common.world;

public final class TerrainPalette {
    private TerrainPalette(){}

    public static final int[] ARGB = new int[256];
    static {
	  ARGB[Terrain.OCEAN.id]   = 0xFF1162C2; // biển
	  ARGB[Terrain.PLAIN.id]   = 0xFF8BC34A; // đồng bằng (xanh nhạt)

	  ARGB[Terrain.FOREST.id]  = 0xFF2E7D32; // rừng thường (xanh đậm)
	  ARGB[Terrain.F_FOG.id]   = 0xFF1B5E20; // rừng sương
	  ARGB[Terrain.F_MAGIC.id] = 0xFF004D40; // rừng ma thuật
	  ARGB[Terrain.F_DARK.id]  = 0xFF102A12; // rừng bóng tối (rất tối)

	  ARGB[Terrain.DESERT.id]  = 0xFFE7C388; // sa mạc (cát)
	  ARGB[Terrain.M_ROCK.id]  = 0xFF8C8C8C; // núi (xám)
	  ARGB[Terrain.LAKE.id]    = 0xFF1E6AA8; // hồ

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
