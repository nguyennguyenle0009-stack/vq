package rt.server.world;

import org.junit.jupiter.api.Test;
import rt.server.game.input.InputQueue;
import rt.server.session.SessionRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests removal of players from world and input queue when they disconnect. */
public class WorldRemovePlayerTest {

    @Test
    void removePlayerRemovesStateAndInput() {
        var sessions = new SessionRegistry();
        var world = new World(sessions);
        var inputs = new InputQueue();

        String id = "p1";
        // send an input to create player state
        inputs.offer(id, 1, Map.of("right", true));
        inputs.applyToWorld(world);
        world.step(1.0);

        assertNotNull(world.pos(id), "player should exist after input");

        // simulate disconnect
        world.removePlayer(id);
        inputs.remove(id);

        // next tick should not recreate the player
        inputs.applyToWorld(world);
        world.step(1.0);

        assertNull(world.pos(id), "player should be removed after disconnect");
    }
}