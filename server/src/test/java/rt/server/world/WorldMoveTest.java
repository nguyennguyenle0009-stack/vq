package rt.server.world;

import org.junit.jupiter.api.Test;
import rt.server.session.SessionRegistry;

import static org.junit.jupiter.api.Assertions.*;

public class WorldMoveTest {

    @Test
    void clampAtWall() {
        var sessions = new SessionRegistry();
        var world = new World(sessions);
        world.setMap(TestMaps.wallAtX3());

        String id = "p1";
        world.applyInput(id, false,false,true,false); // right
        world.step(1.0);

        double[] p = world.pos(id);
        assertNotNull(p);
        assertTrue(p[0] < 3.0 + 1e-6, "player should not cross wall at x=3");
    }

    @Test
    void normalizeDiagonal() {
        var sessions = new SessionRegistry();
        var world = new World(sessions);
        world.setMap(TestMaps.openPlain(100, 100));  // ✅ không có tường

        String id = "p2";

        // Tạo player (ensure) và lấy vị trí bắt đầu
        world.applyInput(id, false,false,false,false); // tạo entry
        double[] s0 = world.pos(id).clone();           // mặc định (3,3)

        // up + left trong 1 giây với SPEED=3 tile/s ⇒ quãng đường ≈ 3 tile
        world.applyInput(id, true,false,false,true);
        world.step(1.0);

        double[] p = world.pos(id);
        assertNotNull(p);
        double dist = Math.hypot(p[0]-s0[0], p[1]-s0[1]);

        // Cho tolerance nhỏ vì double/collision clamp (nếu có) — nhưng ở openPlain thì ~3.0
        assertTrue(dist >= 2.90 && dist <= 3.01, "diagonal speed should be normalized to speed=3 tiles/s");
    }
}
