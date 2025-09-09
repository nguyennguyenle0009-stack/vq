package rt.server.world;

import com.fasterxml.jackson.databind.JsonNode;
import rt.common.net.Jsons;

import java.io.InputStream;
import java.util.Iterator;

/**
 * Loader cho Tiled map (.json). Hỗ trợ:
 *  - Orthogonal
 *  - 1 layer collision (dạng tile layer) → id > 0 xem là solid
 *  - tilewidth == tileheight
 */
public final class TiledJsonLoader {
    private TiledJsonLoader(){}

    public static TileMap load(InputStream in) throws Exception {
        JsonNode root = Jsons.OM.readTree(in);
        int w = root.path("width").asInt();
        int h = root.path("height").asInt();
        int tileW = root.path("tilewidth").asInt();
        int tileH = root.path("tileheight").asInt();
        if (tileW != tileH) throw new IllegalArgumentException("non-square tiles not supported");
        int tile = tileW;

        boolean[][] solid = new boolean[h][w];
        Iterator<JsonNode> it = root.withArray("layers").elements();
        while (it.hasNext()) {
            JsonNode layer = it.next();
            String type = layer.path("type").asText("");
            if (!"tilelayer".equals(type)) continue;
            // Nếu bạn muốn lấy layer theo tên: if (!"collision".equals(layer.path("name").asText())) continue;
            JsonNode data = layer.path("data");
            if (data.isArray() && data.size() == w*h) {
                for (int i=0;i<w*h;i++) {
                    int gid = data.get(i).asInt(0);
                    if (gid > 0) {
                        int x = i % w, y = i / w;
                        solid[y][x] = true;
                    }
                }
                break; // 1 layer
            }
        }
        return new TileMap(w, h, tile, solid);
    }

    public static TileMap loadResource(String path) throws Exception {
        try (InputStream in = TiledJsonLoader.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("tiled map not found: " + path);
            return load(in);
        }
    }
}
