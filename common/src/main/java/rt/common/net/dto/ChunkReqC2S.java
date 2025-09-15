package rt.common.net.dto;

public record ChunkReqC2S(String type, String worldId, int centerCx, int centerCy, int radius) {
    public static final String TYPE = "chunkReq";
    public ChunkReqC2S(String worldId, int centerCx, int centerCy, int radius) {
        this(TYPE, worldId, centerCx, centerCy, radius);
    }
}
