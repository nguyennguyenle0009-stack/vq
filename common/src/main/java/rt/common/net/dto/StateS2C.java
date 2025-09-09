package rt.common.net.dto;

import java.util.Map;

public record StateS2C(String type, int ver, long tick, long ts, Map<String, EntityState> ents) {
    // constructor “chuẩn” với version
    public StateS2C(long tick, long ts, Map<String, EntityState> ents) {
        this("state", 1, tick, ts, ents);
    }
    // constructor “cũ” để giữ tương thích khi ai đó gọi với type/tick/ts/ents
    public StateS2C(String type, long tick, long ts, Map<String, EntityState> ents) {
        this(type, 1, tick, ts, ents);
    }
}
