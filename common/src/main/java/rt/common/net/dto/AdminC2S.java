package rt.common.net.dto;

/** Client gửi lệnh admin qua WS (dev tool). */
public record AdminC2S(String token, String cmd) implements Msg { }
