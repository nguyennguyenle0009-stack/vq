package rt.common.net.dto;

public record ErrorS2C(String type, int ver, String code, String message) {
    public ErrorS2C(String code, String message) { this("error", 1, code, message); }
}
