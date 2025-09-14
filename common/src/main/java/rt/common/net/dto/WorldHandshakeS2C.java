package rt.common.net.dto;

public record WorldHandshakeS2C(
        String type,
        String worldId,
        long seed,
        int tile,
        int chunkSize,
        String tileset,
        int tilesetCols,
        int viewDist
) implements Msg {
    public static final String TYPE = "worldHandshake";
}
