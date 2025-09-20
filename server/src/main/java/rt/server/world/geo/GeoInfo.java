package rt.server.world.geo;

public record GeoInfo(
        int terrainId, 
        String terrainName,
        int continentId, 
        String continentName,
        int seaId, 
        String seaName
) {}
