package rt.common.world;

import java.util.Collections;
import java.util.List;

/** Kết quả tra cứu vị trí: tile hiện tại + chuỗi địa danh theo cấp độ. */
public record LocationSummary(int baseId, int overlayId, boolean blocked, List<GeoFeature> hierarchy) {
    public LocationSummary {
        hierarchy = hierarchy == null ? List.of() : List.copyOf(hierarchy);
    }

    public GeoFeature level(int level) {
        for (GeoFeature f : hierarchy) {
            if (f.level() == level) return f;
        }
        return null;
    }

    public List<GeoFeature> hierarchy() {
        return Collections.unmodifiableList(hierarchy);
    }
}
