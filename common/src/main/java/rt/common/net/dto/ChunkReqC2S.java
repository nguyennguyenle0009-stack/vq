package rt.common.net.dto;

public record ChunkReqC2S(String type, int cx, int cy) implements Msg {
    public ChunkReqC2S(int cx, int cy){ this("chunk_req", cx, cy); }
}
