package rt.common.world;

public final class TerrainPalette {
    private TerrainPalette(){}

    public static final int[] ARGB = new int[256];
    static {
        ARGB[Terrain.OCEAN.id]    = 0xFF1162C2; // biển
        ARGB[Terrain.PLAIN.id]    = 0xFF7BC043; // đồng bằng
        ARGB[Terrain.FOREST.id]   = 0xFF2F8F46; // rừng
        ARGB[Terrain.DESERT.id]   = 0xFFE7C388; // sa mạc
        ARGB[Terrain.MOUNTAIN.id] = 0xFF8C8C8C; // núi
    }
    public static int color(int id){
        int c = ARGB[id & 0xFF];
        return c != 0 ? c : 0xFF808080;
    }
}
