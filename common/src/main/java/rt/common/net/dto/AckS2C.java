package rt.common.net.dto;

public record AckS2C(String type, int seq) {
    public AckS2C(int seq){ this("ack", seq); }
}
