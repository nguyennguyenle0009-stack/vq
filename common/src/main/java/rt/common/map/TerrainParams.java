package rt.common.map;

import java.util.Map;
public record TerrainParams(Map<String, Double> num, Map<String, String> str) {
    public double num(String key, double def){ return num!=null && num.containsKey(key) ? num.get(key) : def; }
    public String str(String key, String def){ return str!=null && str.containsKey(key) ? str.get(key) : def; }
    public static TerrainParams empty(){ return new TerrainParams(java.util.Collections.emptyMap(), java.util.Collections.emptyMap()); }
}
