package rt.server.world;

import rt.common.world.BiomeId;

import java.awt.Color;

public final class BiomePalette {
    private BiomePalette() {}

    public static Color color(byte id) {
        return switch (id & 0xFF) {
            case BiomeId.OCEAN -> new Color(30, 90, 160);
            case BiomeId.LAND -> new Color(159, 177, 140);
            case BiomeId.PLAIN -> new Color(191, 220, 166);
            case BiomeId.DESERT -> new Color(233, 223, 179);
            case BiomeId.PLAIN_WEIRD -> new Color(198, 232, 208);
            case BiomeId.FOREST -> new Color(61, 122, 58);
            case BiomeId.FOREST_FOG -> new Color(75, 138, 106);
            case BiomeId.FOREST_MAGIC -> new Color(107, 108, 207);
            case BiomeId.FOREST_WEIRD -> new Color(63, 94, 47);
            case BiomeId.FOREST_DARK -> new Color(36, 53, 31);
            case BiomeId.LAKE -> new Color(26, 111, 175);
            case BiomeId.RIVER -> new Color(42, 149, 232);
            case BiomeId.MOUNTAIN_SNOW -> new Color(231, 236, 239);
            case BiomeId.MOUNTAIN_VOLCANO -> new Color(156, 59, 36);
            case BiomeId.MOUNTAIN_FOREST -> new Color(82, 113, 83);
            case BiomeId.MOUNTAIN_ROCK -> new Color(124, 124, 124);
            case BiomeId.VILLAGE -> new Color(206, 181, 140);
            default -> Color.MAGENTA;
        };
    }
}
