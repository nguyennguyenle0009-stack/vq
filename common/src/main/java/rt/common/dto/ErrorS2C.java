package rt.common.dto;
// lỗi chuẩn hoá
public record ErrorS2C(String type, String code, String message) implements Msg {}
