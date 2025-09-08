package rt.common.net.dto;

public record InputC2S(String type, int seq, Keys keys) {
    public InputC2S(int seq, Keys keys){ this("input", seq, keys); }
}
