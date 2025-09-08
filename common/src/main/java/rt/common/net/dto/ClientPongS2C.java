package rt.common.net.dto;

public record ClientPongS2C(String type, long ns) {
    public ClientPongS2C(long ns){ this("cpong", ns); }
}
