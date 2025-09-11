package rt.server.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Phác test rate-limit cấp ý tưởng (không phụ thuộc nội bộ InputQueue).
 *  Bạn có thể thay bằng test trực tiếp InputQueue nếu class đã có counter droppedCount(id).
 */
public class RateLimitSketchTest {
  @Test void simple_math_over_60_per_sec() {
    int limit = 60;
    int coming = 150; // giả lập 150 input/s
    int dropped = Math.max(0, coming - limit);
    assertTrue(dropped > 0);
  }
}

