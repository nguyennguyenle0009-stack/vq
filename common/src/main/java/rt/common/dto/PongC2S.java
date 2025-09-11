package rt.common.dto;
// client → server
public record PongC2S(String type, long ts) implements Msg {}