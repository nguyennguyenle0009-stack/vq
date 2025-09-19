package rt.common.world.atlas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Metadata cho bộ tile atlas TMS. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtlasMeta(
        @JsonProperty("tileSizePx") int tileSizePx,
        @JsonProperty("tilePixelSize") int tilePixelSize,
        @JsonProperty("originTileX") int originTileX,
        @JsonProperty("originTileY") int originTileY,
        @JsonProperty("widthTiles") int widthTiles,
        @JsonProperty("heightTiles") int heightTiles,
        @JsonProperty("levels") Level[] levels) {

    public int tilePixelSize() {
        return tilePixelSize > 0 ? tilePixelSize : 32;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Level(
            @JsonProperty("z") int z,
            @JsonProperty("tilesX") int tilesX,
            @JsonProperty("tilesY") int tilesY) {

        public int tileSpanTiles(int tileSizePx) {
            return tileSizePx * (1 << z);
        }

        public int tileSpanWorldPx(int tileSizePx, int tilePixelSize) {
            return tileSpanTiles(tileSizePx) * tilePixelSize;
        }
    }
}

