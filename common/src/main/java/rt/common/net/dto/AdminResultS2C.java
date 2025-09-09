package rt.common.net.dto;

public record AdminResultS2C(String type, int ver, boolean ok, String msg) {
    public AdminResultS2C(boolean ok, String msg) { this("admin_result", 1, ok, msg); }
}
