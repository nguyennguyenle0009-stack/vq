package rt.common.dto;
// client → server
public record InputC2S(String type, int seq, Keys keys) implements Msg {
    public record Keys(boolean up, boolean down, boolean left, boolean right){}
}