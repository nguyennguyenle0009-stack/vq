package rt.common.net;

/** Mã lỗi chuẩn dùng chung client/server. */
public final class ErrorCodes {
    private ErrorCodes(){}
    public static final String BAD_SCHEMA         = "BAD_SCHEMA";
    public static final String PAYLOAD_TOO_LARGE  = "PAYLOAD_TOO_LARGE";
    public static final String ORIGIN_FORBIDDEN   = "ORIGIN_FORBIDDEN";
    public static final String ADMIN_UNAUTHORIZED = "ADMIN_UNAUTHORIZED";
    public static final String UNKNOWN_TYPE       = "UNKNOWN_TYPE";
    public static final String RATE_LIMIT_INPUT   = "RATE_LIMIT_INPUT";
}
