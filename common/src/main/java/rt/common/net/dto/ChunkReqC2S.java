package rt.common.net.dto;

public record ChunkReqC2S(String type, String worldId, int centerCx, int centerCy, int radius) implements Msg {
    public static final String TYPE = "chunkReq";
}
