package rt.server.game.model;

import org.junit.jupiter.api.Test;

import rt.client.model.WorldModel;

import static org.junit.jupiter.api.Assertions.*;

public class WorldPredictionTest {
  @Test void predict_then_ack_reconcile() {
    WorldModel m = new WorldModel();
    m.setYou("A"); m.spawnAt(3,3);
    m.onInputSent(1, false,false,true,false, System.currentTimeMillis()); // left
    m.tickLocalPrediction(0.1, false,false,true,false);
    var p1 = m.getPredictedYou(); assertNotNull(p1);
    assertTrue(p1.x < 3.0);

    m.onAck(1);
    m.reconcileFromServer(p1.x + 0.05, p1.y, 1);
    var p2 = m.getPredictedYou();
    assertTrue(Math.abs(p2.x - (p1.x + 0.05)) < 0.2);
  }
}