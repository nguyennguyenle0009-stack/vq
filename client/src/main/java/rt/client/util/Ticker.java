package rt.client.util;

import javax.swing.*;

/** Tiện ích dùng Swing Timer để chạy callback đều đặn (FPS). */
public class Ticker {
    public static Timer start(int fps, Runnable tick){
        int interval = Math.max(1, 1000 / Math.max(1, fps));
        Timer t = new Timer(interval, e -> tick.run());
        t.start();
        return t;
    }
}
