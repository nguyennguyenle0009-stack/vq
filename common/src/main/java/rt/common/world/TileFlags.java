// rt/common/world/TileFlags.java
package rt.common.world;
public final class TileFlags {
    public static final byte BEACH      = 1 << 0;
    public static final byte MTN_BUFFER = 1 << 1; // đệm quanh núi (cấm rải cây/props)
    private TileFlags(){}
}
