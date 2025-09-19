package rt.common.map;

import com.fasterxml.jackson.databind.JsonNode;
import rt.common.net.Jsons;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Parser cho định dạng map v2 (đa layer). */
public final class MapV2 {
    public record Layer(String name, String type, int[][] ints, String[] rows) {}

    private final int width;
    private final int height;
    private final int tileSize;
    private final List<Layer> layers;

    private MapV2(int width, int height, int tileSize, List<Layer> layers) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.layers = List.copyOf(layers);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int tileSize() { return tileSize; }
    public List<Layer> layers() { return layers; }

    public Layer layer(String name) {
        if (name == null) return null;
        for (Layer layer : layers) {
            if (layer.name() != null && layer.name().equalsIgnoreCase(name)) return layer;
        }
        return null;
    }

    public static MapV2 parse(InputStream in) throws IOException {
        return parse(Jsons.OM.readTree(in));
    }

    public static MapV2 parse(byte[] json) throws IOException {
        return parse(Jsons.OM.readTree(json));
    }

    public static MapV2 parse(JsonNode root) {
        int width = root.path("width").asInt();
        int height = root.path("height").asInt();
        int tileSize = root.path("tileSize").asInt(32);

        List<Layer> layers = new ArrayList<>();
        for (JsonNode layerNode : root.withArray("layers")) {
            String name = layerNode.path("name").asText("");
            String type = layerNode.path("type").asText("");

            int[][] ints = null;
            String[] rows = null;
            JsonNode data = layerNode.get("data");
            if (data != null) {
                if (data.isArray()) {
                    if (data.size() > 0 && data.get(0).isArray()) {
                        ints = new int[height][width];
                        int y = 0;
                        for (JsonNode row : data) {
                            int x = 0;
                            for (JsonNode cell : row) {
                                if (y < height && x < width) {
                                    ints[y][x] = cell.asInt();
                                }
                                x++;
                            }
                            y++;
                            if (y >= height) break;
                        }
                    } else if (data.size() > 0 && data.get(0).isNumber()) {
                        ints = new int[height][width];
                        int idx = 0;
                        for (JsonNode cell : data) {
                            if (idx >= width * height) break;
                            ints[idx / width][idx % width] = cell.asInt();
                            idx++;
                        }
                    } else if (data.size() > 0 && data.get(0).isTextual()) {
                        rows = new String[Math.min(height, data.size())];
                        int y = 0;
                        for (JsonNode row : data) {
                            rows[y] = normalizeRow(row.asText(), width);
                            y++;
                            if (y >= rows.length) break;
                        }
                        if (rows.length < height) {
                            rows = Arrays.copyOf(rows, height);
                            for (int i = y; i < height; i++) rows[i] = "";
                        }
                        for (int i = 0; i < rows.length; i++) {
                            if (rows[i] == null || rows[i].isEmpty()) rows[i] = repeat('.', width);
                        }
                    }
                } else if (data.isTextual()) {
                    String[] parts = data.asText().split("\\R");
                    rows = new String[height];
                    for (int y = 0; y < height; y++) {
                        rows[y] = (y < parts.length) ? normalizeRow(parts[y], width) : repeat('.', width);
                    }
                }
            }

            layers.add(new Layer(name, type, ints, rows));
        }

        return new MapV2(width, height, tileSize, layers);
    }

    private static String normalizeRow(String row, int width) {
        if (row == null) row = "";
        if (row.length() == width) return row;
        if (row.length() > width) return row.substring(0, width);
        StringBuilder sb = new StringBuilder(width);
        sb.append(row);
        while (sb.length() < width) sb.append('.');
        return sb.toString();
    }

    private static String repeat(char ch, int count) {
        char[] arr = new char[count];
        Arrays.fill(arr, ch);
        return new String(arr);
    }

    /**
     * Trả về mảng solidLines ('.'/'#') dựa trên layer "collision" nếu có.
     * Nếu thiếu layer collision => trả về rỗng (tất cả '.').
     */
    public String[] collisionAsSolidLines() {
        Layer layer = layer("collision");
        if (layer == null) {
            String[] empty = new String[height];
            Arrays.fill(empty, repeat('.', width));
            return empty;
        }

        if (layer.rows() != null) {
            String[] rows = Arrays.copyOf(layer.rows(), height);
            for (int y = 0; y < height; y++) {
                String src = (y < rows.length && rows[y] != null) ? rows[y] : "";
                char[] out = new char[width];
                for (int x = 0; x < width; x++) {
                    char c = x < src.length() ? src.charAt(x) : '.';
                    out[x] = solidFromChar(c);
                }
                rows[y] = new String(out);
            }
            return rows;
        }

        if (layer.ints() != null) {
            int[][] data = layer.ints();
            String[] rows = new String[height];
            for (int y = 0; y < height; y++) {
                char[] out = new char[width];
                for (int x = 0; x < width; x++) {
                    int v = (y < data.length && x < data[y].length) ? data[y][x] : 0;
                    out[x] = v != 0 ? '#' : '.';
                }
                rows[y] = new String(out);
            }
            return rows;
        }

        String[] rows = new String[height];
        Arrays.fill(rows, repeat('.', width));
        return rows;
    }

    private static char solidFromChar(char c) {
        return switch (Character.toLowerCase(c)) {
            case '#', '1', 'x', 'w', 'b' -> '#';
            default -> '.';
        };
    }
}

