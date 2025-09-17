package rt.server.world;

import java.awt.Color;

public final class BiomePalette {
public static Color color(byte id){
return switch(id){
case 0 -> new Color( 30, 90,160); // OCEAN
case 1 -> new Color(180,180,180); // CONTINENT baseline (không dùng trực tiếp)
case 2 -> new Color(195,215,140); // PLAIN
case 3 -> new Color( 60,120, 60); // FOREST
case 4 -> new Color(220,210,160); // DESERT
case 5 -> new Color(130,120,110); // MOUNTAIN
default -> Color.MAGENTA;
};
}
}
