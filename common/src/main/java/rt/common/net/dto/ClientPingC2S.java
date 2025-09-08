package rt.common.net.dto;

// ping client-chủ-động (đo RTT chuẩn client-side)
public record ClientPingC2S(String type, long ns) {
    public ClientPingC2S(long ns){ this("cping", ns); }
}
