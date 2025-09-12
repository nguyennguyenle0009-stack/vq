package rt.common.net.dto;

// ===== Client -> Server =====
public record HelloC2S(String type, String name) implements Msg {
    public HelloC2S(String name){ this("hello", name); }
}
