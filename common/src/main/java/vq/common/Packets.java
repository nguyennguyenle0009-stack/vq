package vq.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Packets {
    /** Client → Server: trạng thái phím (giữ nguyên cho đến khi client đổi). */
    public static class C2SInput {
        public String op = "input";
        public int seq;                     // số thứ tự gói tin (dùng cho ack)
        public Map<String, Boolean> press;  // {"up":true,"down":false,"left":false,"right":true}
    }
    /** Server → Client: xác nhận đã nhận input seq (phục vụ reconciliation sau này). */
    public static class S2CAck {
        public String op = "ack";
        public int seq;
        public long serverTime;
        public S2CAck() {}
        public S2CAck(int seq, long serverTime) { this.seq = seq; this.serverTime = serverTime; }
    }
    /** Server → Client: chào khi kết nối để client biết playerId của mình. */
    public static class S2CHello {
        public String op = "hello";
        public String playerId;
        public S2CHello() {}
        public S2CHello(String id) { this.playerId = id; }
    }
    /** Server → Client: snapshot/push trạng thái thế giới. */
    public static class S2CState {
        public String op = "state";
        public long tick;                      // tick số mấy (60 TPS)
        public Player you;                     // trạng thái của chính người chơi
        public java.util.Map<String, Player> ents; // những entity khác (player khác)
        public static class Player { public double x, y; public double vx, vy; }
    }
}
