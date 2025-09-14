package rt.common.map;

public final class Grid {
    public static final int TILE  = 32;
    public static final int CHUNK = 32;
    private Grid() {}
    public static int floorDiv(int a, int b){ int q=a/b, r=a%b; return (r!=0 && ((a^b)<0))? q-1 : q; }
    public static int floorMod(int a, int b){ int r=a%b; return (r<0)? r+b : r; }
    public static int chunkX(int tileX){ return floorDiv(tileX, CHUNK); }
    public static int chunkY(int tileY){ return floorDiv(tileY, CHUNK); }
    public static int localX(int tileX){ return floorMod(tileX, CHUNK); }
    public static int localY(int tileY){ return floorMod(tileY, CHUNK); }
}
