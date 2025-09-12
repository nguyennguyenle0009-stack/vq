package rt.client.game.ui;

import rt.client.net.NetClient;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

// deprecated, superseded by NetClient cping/cpong
/** Chủ động ping server mỗi 1s bằng nanoTime, đo RTT chính xác trên client. */
public final class PingMonitor {
    private final NetClient net;
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong lastNs = new AtomicLong(-1);
    private volatile int pingMs = -1;

    public PingMonitor(NetClient net) {
        this.net = net;
        // khi NetClient nhận "cpong", nó sẽ gọi setPong(ns)
        net.setOnClientPong(this::onPong);
    }

    public void start() {
        ses.scheduleAtFixedRate(() -> {
            long ns = System.nanoTime();
            lastNs.set(ns);
            net.sendClientPing(ns); // gửi {"type":"cping","ns":<nano>}
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() { ses.shutdownNow(); }

    private void onPong(long nsEcho) {
        long sent = lastNs.getAndSet(-1);
        if (sent > 0 && nsEcho == sent) {
            long rttNs = System.nanoTime() - sent;
            pingMs = (int)Math.max(0, Math.round(rttNs / 1_000_000.0));
        }
    }

    public int pingMs() { return pingMs; } // -1 nếu chưa đo được
}
