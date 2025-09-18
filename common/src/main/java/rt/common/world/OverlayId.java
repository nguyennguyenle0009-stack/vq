package rt.common.world;

/** ID cho layer2 (các cấu trúc tĩnh vẽ chồng lên địa hình nền). */
public final class OverlayId {
    private OverlayId() {}

    public static final int NONE  = 0;
    public static final int HOUSE = 1;  // nhà/công trình chắn đường
    public static final int ROAD  = 2;  // đường đi (walkable)
    public static final int FARM  = 3;  // ruộng / trang trí (walkable)
}
