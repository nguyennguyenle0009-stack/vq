package rt.common.net.dto;

public record ChunkS2C(
    String type,
    String worldId,
    int cx,
    int cy,
    int version,
    int w,
    int h,
    int[] tiles,
    String collisionRLE // Base64 string
) {
    public static final String TYPE = "chunk";
    public ChunkS2C(String worldId, int cx, int cy, int version,
                    int w, int h, int[] tiles, String collisionRLE) {
        this(TYPE, worldId, cx, cy, version, w, h, tiles, collisionRLE);
    }
}
