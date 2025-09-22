package rt.common.world;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

public enum Terrain {
    OCEAN      (BiomeId.OCEAN,     "Biển khơi",         true,  0),

    PLAIN      (BiomeId.PLAIN,     "Đồng bằng",         false, 3),

    // RỪNG – tất cả blocked theo yêu cầu
    FOREST     (BiomeId.FOREST,    "Rừng thường",       false,  3),
    F_FOG      (BiomeId.F_FOG,     "Rừng sương",        false,  3),
    F_MAGIC    (BiomeId.F_MAGIC,   "Rừng ma thuật",     false,  3),
    F_DARK     (BiomeId.F_DARK,    "Rừng bóng tối",     false,  3),
    DESERT     (BiomeId.DESERT,    "Sa mạc",            false,  3),
    M_ROCK     (BiomeId.M_ROCK,    "Núi",               true,  4),
    LAKE       (BiomeId.LAKE,      "Hồ",       			true,  4),

    // Các id còn lại giữ làm tương thích nhưng sẽ không được sinh ra
    PLAIN_WEIRD(BiomeId.PLAIN_WEIRD,"Plain* ", false, 3),
    RIVER      (BiomeId.RIVER,     "Sông",     false, 4),
    F_WEIRD    (BiomeId.F_WEIRD,   "Rừng lạ",  false, 3),
    M_SNOW     (BiomeId.M_SNOW,    "Núi tuyết",true,  4),
    M_VOLCANO  (BiomeId.M_VOLCANO, "Núi lửa",  true,  4),
    M_FOREST   (BiomeId.M_FOREST,  "Núi rừng", true,  4),
    MOUNTAIN   (5,                 "Núi*",     true,  4);

    public final int id; public final String name; public final boolean blocked; public final int level;
    Terrain(int id, String name, boolean blocked, int level){ this.id=id; this.name=name; this.blocked=blocked; this.level=level; }
    private static final Map<Integer, Terrain> BY_ID =
        Arrays.stream(values()).collect(Collectors.toMap(t->t.id, t->t));
    public static Terrain byId(int id){ return BY_ID.getOrDefault(id, OCEAN); }
}
