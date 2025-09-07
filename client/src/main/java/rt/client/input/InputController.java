package rt.client.input;

import java.awt.event.KeyEvent;

/** Gom input từ bàn phím thành press map và gửi qua NetClient. */
public class InputController {

        public volatile boolean up;
        public volatile boolean down;
        public volatile boolean left;
        public volatile boolean right;
        public void set(KeyEvent e, boolean v) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_W, KeyEvent.VK_UP -> up = v;
                case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = v;
                case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = v;
                case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = v;
            }
        }
    
}
