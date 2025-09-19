package rt.client.input;

import java.awt.event.KeyEvent;

/** Trạng thái input WASD/Arrow, tách riêng khỏi ClientApp. */
public class InputState {
    public volatile boolean up, down, left, right;

    public void set(KeyEvent e, boolean v) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = v;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = v;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = v;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = v;
            default -> {}
        }
    }

    public void clear() {
        up = down = left = right = false;
    }
}
