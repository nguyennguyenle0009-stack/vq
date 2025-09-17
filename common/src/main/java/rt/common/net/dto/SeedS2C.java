package rt.common.net.dto;
public record SeedS2C(String type, long seed, int chunkSize, int tileSize) implements Msg {
    public SeedS2C(long seed,int chunkSize,int tileSize){ this("seed", seed, chunkSize, tileSize); }
}
