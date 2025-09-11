package rt.common.dto;
// client → server
public record HelloC2S(String type, String name) implements Msg {}
