package rt.common.net.dto;

public record ClientPongS2C(String type, long ns) implements Msg {
    public ClientPongS2C(long ns){ this("cpong", ns); }
}
