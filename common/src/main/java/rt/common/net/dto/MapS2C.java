package rt.common.net.dto;

public record MapS2C(String type, int ver, int tile, int w, int h, String[] solidLines) implements Msg {
    public MapS2C(int tile, int w, int h, String[] solidLines) {
        this("map", 1, tile, w, h, solidLines);
    }
}
