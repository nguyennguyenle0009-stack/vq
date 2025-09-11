package rt.server.game.loop;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra "giữ state mới nhất" (last-write-wins) với slot per-channel. */
public class StreamerBackpressureTest {
  @Test void last_message_wins() {
    AtomicReference<Map<String,Object>> slot = new AtomicReference<>();
    slot.set(Map.of("tick", 1));
    slot.set(Map.of("tick", 2));
    slot.set(Map.of("tick", 3));
    Map<String,Object> send = slot.getAndSet(null);
    assertEquals(3, ((Number)send.get("tick")).intValue());
    assertNull(slot.get());
  }
}
