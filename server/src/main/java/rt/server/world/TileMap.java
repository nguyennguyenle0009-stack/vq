package rt.server.world;

import com.fasterxml.jackson.databind.JsonNode;
import rt.common.net.Jsons;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Tile map đơn giản: '#' là tường (solid), '.' là trống. Đơn vị: tile. */
public class TileMap {
    public final int w, h, tile; // tile = 32 px (render), logic dùng đơn vị tile
    private final boolean[][] solid; // [y][x]

    public TileMap(int w, int h, int tile, boolean[][] solid) {
        this.w = w; this.h = h; this.tile = tile;
        this.solid = Objects.requireNonNull(solid);
    }

    public boolean blocked(int tx, int ty) {
        return tx < 0 || ty < 0 || tx >= w || ty >= h || solid[ty][tx];
    }

    /** Load JSON dạng:
     * { "tileSize":32,"width":28,"height":18,"solid":[ "########", "#......#", ... ] }
     */
    public static TileMap load(InputStream in) throws Exception {
        JsonNode root = Jsons.OM.readTree(in);
        int tile = root.path("tileSize").asInt(32);
        int W = root.path("width").asInt();
        int H = root.path("height").asInt();
        boolean[][] s = new boolean[H][W];
        int y = 0;
        for (JsonNode row : root.withArray("solid")) {
            String line = row.asText();
            for (int x = 0; x < Math.min(W, line.length()); x++) {
                char c = line.charAt(x);
                s[y][x] = (c == '#');
            }
            y++;
            if (y >= H) break;
        }
        return new TileMap(W, H, tile, s);
    }

    public static TileMap loadResource(String path) throws Exception {
        try (InputStream in = TileMap.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("map not found: " + path);
            return load(in);
        }
    }

    /** Map demo nhỏ nếu chưa có resource. */
    public static TileMap demo() {
        String[] rows = {
                "############################",
                "#............##............#",
                "#............##............#",
                "#..........................#",
                "#....######........######..#",
                "#..........................#",
                "#............##............#",
                "#............##............#",
                "############################"
        };
        int h = rows.length, w = rows[0].length();
        boolean[][] s = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                s[y][x] = rows[y].charAt(x) == '#';
        return new TileMap(w, h, 32, s);
    }
}
