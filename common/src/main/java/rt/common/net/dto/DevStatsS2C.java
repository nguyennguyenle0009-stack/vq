package rt.common.net.dto;

public record DevStatsS2C(String type, int ver, long tick, long ts,
                          int ents, int droppedInputs, int streamerSkips, boolean writable) {
    public DevStatsS2C(long tick, long ts, int ents, int droppedInputs, int streamerSkips, boolean writable) {
        this("dev_stats", 1, tick, ts, ents, droppedInputs, streamerSkips, writable);
    }
}
