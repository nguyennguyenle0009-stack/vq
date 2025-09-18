package rt.common.world;

import java.util.Objects;

/**
 * Mô tả một thực thể địa lý (biển, lục địa, biome, tiểu-biome...).
 */
public record GeoFeature(int level, Kind kind, String code, String name) {
    public GeoFeature {
        Objects.requireNonNull(kind, "kind");
        code = code == null ? "" : code;
        name = name == null ? "" : name;
    }

    public enum Kind {
        OCEAN,
        CONTINENT,
        PLAIN,
        PLAIN_VARIANT,
        DESERT,
        FOREST,
        FOREST_SUB,
        LAKE,
        RIVER,
        MOUNTAIN,
        VILLAGE
    }
}
