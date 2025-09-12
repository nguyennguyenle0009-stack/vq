package rt.common.net.dto;

public record PingS2C(String type, long ts) implements Msg {
    public PingS2C(long ts){ this("ping", ts); }
}
