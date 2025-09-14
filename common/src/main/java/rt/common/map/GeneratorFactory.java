package rt.common.map;

public final class GeneratorFactory {
    private GeneratorFactory(){}
    public static TerrainGenerator create(String type, TerrainParams params){
        String t = (type == null || type.isBlank()) ? "noise" : type;
        TerrainGenerator g = switch (t) {
            case "desert"   -> new DesertTerrain();
            case "islands"  -> new IslandsTerrain();
            case "mountain" -> new MountainTerrain();
            default         -> new NoiseTerrainGenerator();
        };
        if (g instanceof TerrainGeneratorEx gx) gx.configure(params == null ? TerrainParams.empty() : params);
        return g;
    }
}
