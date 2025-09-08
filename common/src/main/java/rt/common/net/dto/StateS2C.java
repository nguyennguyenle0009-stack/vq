package rt.common.net.dto;

import java.util.Map;

public record StateS2C(String type, long tick, long ts, Map<String, EntityState> ents) {
    public StateS2C(long tick, long ts, Map<String, EntityState> ents){
        this("state", tick, ts, ents);
    }
}
