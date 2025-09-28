package rt.common.net.dto;

/** Cấu hình thế giới gửi từ server -> client, rất nhẹ. */
public record WorldConfigS2C(
    String type,      // luôn = "world_config"
    long seed,
    int chunkSize,
    int tileSize,
    double forestRatio,
    double plainRatio
) {
    public WorldConfigS2C(long seed, int chunkSize, int tileSize,
                          double forestRatio, double plainRatio) {
        this("world_config", seed, chunkSize, tileSize, forestRatio, plainRatio);
    }
}
