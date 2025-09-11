package rt.common.dto;

public record AckS2C(String type, int seq) implements Msg {}