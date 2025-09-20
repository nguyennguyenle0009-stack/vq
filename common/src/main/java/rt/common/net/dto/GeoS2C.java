package rt.common.net.dto;

public record GeoS2C(
        String type, long gx, long gy,
        int terrainId, String terrainName,
        int continentId, String continentName,
        int seaId, String seaName
) {
    public GeoS2C(long gx, long gy,
                  int terrainId, String terrainName,
                  int continentId, String continentName,
                  int seaId, String seaName) {
        this("geo_info", gx, gy, terrainId, terrainName, continentId, continentName, seaId, seaName);
    }
}
