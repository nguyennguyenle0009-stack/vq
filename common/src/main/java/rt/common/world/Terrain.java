package rt.common.world;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

public enum Terrain {
    OCEAN   (0, "Biển",       true),
    PLAIN   (2, "Đồng bằng",  false),
    FOREST  (3, "Rừng rậm",   false),
    DESERT  (4, "Sa mạc",     false),
    MOUNTAIN(5, "Núi",        true);

    public final int id; public final String name; public final boolean blocked;
    Terrain(int id, String name, boolean blocked){ this.id=id; this.name=name; this.blocked=blocked; }

    private static final Map<Integer, Terrain> BY_ID =
        Arrays.stream(values()).collect(Collectors.toMap(t->t.id, t->t));
    public static Terrain byId(int id){ return BY_ID.getOrDefault(id, OCEAN); }
}
