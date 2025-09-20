package rt.client.input;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

/** Trạng thái input & key bindings cho WASD + Arrow. */
public class InputState {
    public volatile boolean up, down, left, right;

    // đếm số phím đang giữ cho từng hướng (để xử lý nhấn đồng thời)
    private int upN, downN, leftN, rightN;

    public void reset() {
        up = down = left = right = false;
        upN = downN = leftN = rightN = 0;
    }

    /** Gắn KeyBindings vào RootPane. allowMove==true thì mới nhận input (VD: !mapOpen). */
    public void bind(JRootPane root, BooleanSupplier allowMove) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        // WASD
        register(im, am, KeyEvent.VK_W, () -> incUp(),    () -> decUp(),    allowMove);
        register(im, am, KeyEvent.VK_S, () -> incDown(),  () -> decDown(),  allowMove);
        register(im, am, KeyEvent.VK_A, () -> incLeft(),  () -> decLeft(),  allowMove);
        register(im, am, KeyEvent.VK_D, () -> incRight(), () -> decRight(), allowMove);

        // Arrow keys
        register(im, am, KeyEvent.VK_UP,    () -> incUp(),    () -> decUp(),    allowMove);
        register(im, am, KeyEvent.VK_DOWN,  () -> incDown(),  () -> decDown(),  allowMove);
        register(im, am, KeyEvent.VK_LEFT,  () -> incLeft(),  () -> decLeft(),  allowMove);
        register(im, am, KeyEvent.VK_RIGHT, () -> incRight(), () -> decRight(), allowMove);
    }

    private void incUp(){ up = (++upN)   >0; }
    private void decUp(){ up = (--upN)<=0 ? (upN=0)>=0 && false : true; }

    private void incDown(){ down = (++downN) >0; }
    private void decDown(){ down = (--downN)<=0 ? (downN=0)>=0 && false : true; }

    private void incLeft(){ left = (++leftN) >0; }
    private void decLeft(){ left = (--leftN)<=0 ? (leftN=0)>=0 && false : true; }

    private void incRight(){ right = (++rightN) >0; }
    private void decRight(){ right = (--rightN)<=0 ? (rightN=0)>=0 && false : true; }
    
    /** (tùy chọn) API cũ – nếu nơi nào còn dùng. */
    public void set(java.awt.event.KeyEvent e, boolean v) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP    -> { if (v) incUp();    else decUp(); }
            case KeyEvent.VK_S, KeyEvent.VK_DOWN  -> { if (v) incDown();  else decDown(); }
            case KeyEvent.VK_A, KeyEvent.VK_LEFT  -> { if (v) incLeft();  else decLeft(); }
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> { if (v) incRight(); else decRight(); }
        }
    }

    // helper: tạo action key cho pressed/released
    private static void register(InputMap im, ActionMap am, int vk,
                                 Runnable onPress, Runnable onRelease,
                                 BooleanSupplier allow) {
        String p = "mv_" + vk + "_p", r = "mv_" + vk + "_r";
        im.put(KeyStroke.getKeyStroke(vk, 0, false), p); // pressed
        im.put(KeyStroke.getKeyStroke(vk, 0, true ), r); // released
        am.put(p, new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(allow.getAsBoolean()) onPress.run(); }});
        am.put(r, new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ if(allow.getAsBoolean()) onRelease.run(); }});
    }
}
