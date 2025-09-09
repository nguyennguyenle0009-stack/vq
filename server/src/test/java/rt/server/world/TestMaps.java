package rt.server.world;

public final class TestMaps {
    private TestMaps(){}

    /** Map 8x5, tường ở cột x=3 (đang dùng cho clamp test). */
    public static TileMap wallAtX3() {
        String[] rows = {
                "########",
                "#..#...#",
                "#..#...#",
                "#..#...#",
                "########"
        };
        int h = rows.length, w = rows[0].length();
        boolean[][] s = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                s[y][x] = rows[y].charAt(x) == '#';
        return new TileMap(w, h, 32, s);
    }

    /** Map trống kích thước w×h, không có tường (dùng cho test vận tốc). */
    public static TileMap openPlain(int w, int h) {
        boolean[][] s = new boolean[h][w]; // mặc định false = không tường
        return new TileMap(w, h, 32, s);
    }
}
