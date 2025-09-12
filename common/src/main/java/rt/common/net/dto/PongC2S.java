package rt.common.net.dto;

public record PongC2S(String type, long ts) implements Msg {
    public PongC2S(long ts){ this("pong", ts); }
}
