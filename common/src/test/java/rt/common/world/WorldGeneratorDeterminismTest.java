package rt.common.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WorldGeneratorDeterminismTest {
    @Test
    public void sameSeedSameResult() {
        WorldGenConfig cfg = new WorldGenConfig(42L, 0.5, 0.35);
        WorldGenerator g1 = new WorldGenerator(cfg);
        WorldGenerator g2 = new WorldGenerator(cfg);

        for (int cy=-2; cy<=2; cy++)
            for (int cx=-2; cx<=2; cx++){
                ChunkData a = g1.generate(cx,cy);
                ChunkData b = g2.generate(cx,cy);
                assertEquals(a.size, b.size);
                for (int i=0;i<a.layer1.length;i++) assertEquals(a.layer1[i], b.layer1[i]);
            }
    }
}
