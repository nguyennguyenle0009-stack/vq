package rt.server.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

public final class CompatWorlds {
    private static volatile WorldRegistry REG;

    private CompatWorlds(){}

    public static WorldRegistry reg(){ return REG; }

    public static synchronized WorldRegistry initFromClasspathConfig() {
        if (REG != null) return REG;
        try (InputStream in = CompatWorlds.class.getResourceAsStream("/server-worlds.json")) {
            if (in == null) {
                var specs = new ArrayList<WorldRegistry.Spec>();
                var s = new WorldRegistry.Spec();
                s.id = "overworld"; s.type = "noise"; s.seed = 123456789L;
                s.tileset = "/tiles/overworld.png"; s.tilesetCols = 16;
                specs.add(s);
                REG = new WorldRegistry(specs, "overworld", 3);
                return REG;
            }
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(in);
            List<WorldRegistry.Spec> specs = new ArrayList<>();
            for (JsonNode w : root.withArray("worlds")){
                WorldRegistry.Spec s = new WorldRegistry.Spec();
                s.id = w.get("id").asText();
                s.type = w.path("type").asText("noise");
                s.seed = w.get("seed").asLong();
                s.tileset = w.path("tileset").asText("/tiles/overworld.png");
                s.tilesetCols = w.path("tilesetCols").asInt(16);
                if (w.has("params")) {
                    Map<String, Double> p = new HashMap<>();
                    w.get("params").fields().forEachRemaining(e -> p.put(e.getKey(), e.getValue().asDouble()));
                    s.params = p;
                }
                specs.add(s);
            }
            String def = root.path("defaultWorld").asText("overworld");
            int viewDist = root.path("viewDist").asInt(3);
            REG = new WorldRegistry(specs, def, viewDist);
            return REG;
        } catch (Exception e) {
            throw new RuntimeException("Load server-worlds.json failed", e);
        }
    }
}
