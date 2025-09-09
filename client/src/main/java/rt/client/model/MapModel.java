package rt.client.model;

public class MapModel {
    public final int tile;
    public final int w, h;
    public final boolean[][] solid; // [y][x]

    public MapModel(int tile, int w, int h, boolean[][] solid) {
        this.tile = tile; this.w = w; this.h = h; this.solid = solid;
    }

    public static MapModel fromLines(int tile, int w, int h, String[] lines) {
        boolean[][] s = new boolean[h][w];
        int H = Math.min(h, lines.length);
        for (int y = 0; y < H; y++) {
            String row = lines[y];
            int W = Math.min(w, row.length());
            for (int x = 0; x < W; x++) s[y][x] = (row.charAt(x) == '#');
        }
        return new MapModel(tile, w, h, s);
    }
}
