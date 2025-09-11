package rt.common.dto;
// server → client: số liệu dev HUD
public record DevStatsS2C(String type, int ents, int droppedInputs, int streamerSkips, boolean writable) implements Msg {}
