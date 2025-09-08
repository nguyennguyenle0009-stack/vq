package rt.common.net.dto;

// ===== Client -> Server =====
public record HelloC2S(String type, String name) {
    public HelloC2S(String name){ this("hello", name); }
}
