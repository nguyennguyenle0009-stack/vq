package rt.client.model;

public record GeoInfo(long gx,long gy,
                      int terrainId,String terrainName,
                      int continentId,String continentName,
                      int seaId,String seaName) { }
