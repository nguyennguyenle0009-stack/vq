package rt.common.dto;
import java.util.Map;
public record StateS2C(
    String type, long ts, long tick,
    Map<String, EntState> ents
) implements Msg {
    public record EntState(double x, double y){}
}