package rt.common.net.dto;

public record GeoReqC2S(long gx, long gy) {
    public String type() { return "geo_req"; }
}
