package rt.common.net.dto;
public record SeedS2C(String type, long seed, int chunkSize, int tileSize, double plainRatio, double forestRatio) implements Msg {
    public SeedS2C(long seed,int chunkSize,int tileSize,double plainRatio,double forestRatio){ this("seed", seed, chunkSize, tileSize, plainRatio, forestRatio); }
}
