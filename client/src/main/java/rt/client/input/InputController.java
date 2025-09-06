package rt.client.input;

import rt.client.net.NetClient;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** Gom input từ bàn phím thành press map và gửi qua NetClient. */
public class InputController {
    private final NetClient net;
    private final Map<String, Boolean> press = new HashMap<>(Map.of(
            "up", false, "down", false, "left", false, "right", false
    ));

    public InputController(NetClient net){ this.net = net; }

    public KeyAdapter keyListener() {
        return new KeyAdapter() {
            private void update(String key, boolean v) {
                Boolean cur = press.get(key);
                if (cur != null && cur == v) return; // không gửi thừa
                press.put(key, v);
                net.sendInput(net.nextSeq(), press);
            }
            @Override public void keyPressed(KeyEvent e) { onKey(e, true); }
            @Override public void keyReleased(KeyEvent e) { onKey(e, false); }

            private void onKey(KeyEvent e, boolean v) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W, KeyEvent.VK_UP    -> update("up", v);
                    case KeyEvent.VK_S, KeyEvent.VK_DOWN  -> update("down", v);
                    case KeyEvent.VK_A, KeyEvent.VK_LEFT  -> update("left", v);
                    case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> update("right", v);
                }
            }
        };
    }
}
