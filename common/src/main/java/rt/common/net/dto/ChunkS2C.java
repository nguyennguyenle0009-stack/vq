package rt.common.net.dto;

public record ChunkS2C(
        String type,
        String worldId,
        long seed,
        int cx, int cy,
        int tile, int w, int h,
        short[] tiles,
        byte[] solidRLE,
        int version
) implements Msg {
    public static final String TYPE = "chunk";
}
