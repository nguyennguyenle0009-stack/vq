package rt.common.net.dto;

// ===== Server -> Client =====
public record HelloS2C(String type, String you) {
    public HelloS2C(String you){ this("hello", you); }
}
