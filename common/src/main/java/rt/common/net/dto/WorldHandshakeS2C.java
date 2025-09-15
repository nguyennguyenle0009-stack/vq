package rt.common.net.dto;

public record WorldHandshakeS2C(
    String type,
    int protoVersion,
    String worldId,
    int tileSize,
    int chunkSize,
    int viewDist,
    String tileset,
    int tilesetCols
) {
    public static final String TYPE = "worldHandshake";
    public WorldHandshakeS2C(String worldId, int tileSize, int chunkSize,
                             int viewDist, String tileset, int tilesetCols) {
        this(TYPE, 1, worldId, tileSize, chunkSize, viewDist, tileset, tilesetCols);
    }
}
