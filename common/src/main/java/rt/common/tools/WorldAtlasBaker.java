package rt.common.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import rt.common.world.BiomeId;
import rt.common.world.WorldGenConfig;
import rt.common.world.WorldGenerator;
import rt.common.world.atlas.AtlasMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tool CLI để pre-bake world atlas theo định dạng TMS (png 256x256). */
public final class WorldAtlasBaker {
    private static final int TILE_PX = 32;
    private static final int ATLAS_TILE_PX = 256;

    private WorldAtlasBaker() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        long seed = Long.parseLong(opts.getOrDefault("seed", "20250917"));
        double plainRatio = Double.parseDouble(opts.getOrDefault("plain", "0.55"));
        double forestRatio = Double.parseDouble(opts.getOrDefault("forest", "0.35"));
        int radius = Integer.parseInt(opts.getOrDefault("radius", "4096"));
        int maxLevel = Integer.parseInt(opts.getOrDefault("levels", "4"));
        Path outDir = Path.of(opts.getOrDefault("out", "atlas"));

        Files.createDirectories(outDir);

        WorldGenerator generator = new WorldGenerator(new WorldGenConfig(seed, plainRatio, forestRatio));
        WorldGenerator.Sampler sampler = generator.sampler();

        int sizeTiles = radius * 2;
        int originTile = -radius;

        List<AtlasMeta.Level> levels = new ArrayList<>();

        for (int z = 0; z <= maxLevel; z++) {
            int tileSpanTiles = ATLAS_TILE_PX * (1 << z);
            int tilesX = Math.max(1, (int) Math.ceil(sizeTiles / (double) tileSpanTiles));
            int tilesY = Math.max(1, (int) Math.ceil(sizeTiles / (double) tileSpanTiles));
            levels.add(new AtlasMeta.Level(z, tilesX, tilesY));

            for (int ty = 0; ty < tilesY; ty++) {
                for (int tx = 0; tx < tilesX; tx++) {
                    long tileOriginX = originTile + (long) tx * tileSpanTiles;
                    long tileOriginY = originTile + (long) ty * tileSpanTiles;
                    BufferedImage img = new BufferedImage(ATLAS_TILE_PX, ATLAS_TILE_PX, BufferedImage.TYPE_INT_ARGB);
                    int[] buf = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

                    for (int py = 0; py < ATLAS_TILE_PX; py++) {
                        long worldTileY = tileOriginY + (long) py * (1 << z);
                        int row = py * ATLAS_TILE_PX;
                        for (int px = 0; px < ATLAS_TILE_PX; px++) {
                            long worldTileX = tileOriginX + (long) px * (1 << z);
                            int base = sampler.baseId(worldTileX, worldTileY);
                            buf[row + px] = colorFor(base);
                        }
                    }

                    Path tilePath = outDir.resolve(Path.of(Integer.toString(z), Integer.toString(tx)));
                    Files.createDirectories(tilePath);
                    Path file = tilePath.resolve(ty + ".png");
                    ImageIO.write(img, "png", file.toFile());
                    img.flush();
                }
            }
        }

        AtlasMeta meta = new AtlasMeta(ATLAS_TILE_PX, TILE_PX, originTile, originTile, sizeTiles, sizeTiles,
                levels.toArray(new AtlasMeta.Level[0]));
        ObjectMapper om = new ObjectMapper();
        om.writerWithDefaultPrettyPrinter().writeValue(outDir.resolve("meta.json").toFile(), meta);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (String arg : args) {
            if (arg == null) continue;
            String trimmed = arg.trim();
            if (trimmed.startsWith("--")) trimmed = trimmed.substring(2);
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).toLowerCase(Locale.ROOT);
            String value = trimmed.substring(eq + 1);
            out.put(key, value);
        }
        return out;
    }

    private static int colorFor(int id) {
        return switch (id) {
            case BiomeId.OCEAN -> 0xFF1E5AA8;
            case BiomeId.LAND -> 0xFF9FB18C;
            case BiomeId.PLAIN -> 0xFFBFDCA6;
            case BiomeId.DESERT -> 0xFFE9DFB3;
            case BiomeId.PLAIN_WEIRD -> 0xFFC6E8D0;
            case BiomeId.FOREST -> 0xFF3D7A3A;
            case BiomeId.FOREST_FOG -> 0xFF4B8A6A;
            case BiomeId.FOREST_MAGIC -> 0xFF6B6CCF;
            case BiomeId.FOREST_WEIRD -> 0xFF3F5E2F;
            case BiomeId.FOREST_DARK -> 0xFF24351F;
            case BiomeId.LAKE -> 0xFF1A6FAF;
            case BiomeId.RIVER -> 0xFF2A95E8;
            case BiomeId.MOUNTAIN_SNOW -> 0xFFE7ECEF;
            case BiomeId.MOUNTAIN_VOLCANO -> 0xFF9C3B24;
            case BiomeId.MOUNTAIN_FOREST -> 0xFF527153;
            case BiomeId.MOUNTAIN_ROCK -> 0xFF7C7C7C;
            case BiomeId.VILLAGE -> 0xFFCEB58C;
            default -> 0xFF646464;
        };
    }
}

