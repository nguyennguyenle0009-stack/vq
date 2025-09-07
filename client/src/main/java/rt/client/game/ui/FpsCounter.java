package rt.client.game.ui;

/** Đếm FPS dựa trên dt (giây) mỗi frame, có làm mượt EMA. */
public final class FpsCounter {
    private double emaFps = 0;
    private static final double ALPHA = 0.12; // mượt vừa phải

    /** Gọi mỗi frame với dt (giây). */
    public void update(double dt) {
        if (dt <= 0) return;
        double inst = 1.0 / dt;
        emaFps = (emaFps == 0) ? inst : (emaFps + (inst - emaFps) * ALPHA);
    }

    public int fpsInt() { return (int)Math.round(emaFps); }
    public double fpsExact() { return emaFps; }
}
