package rt.client.net.geo;

/**
 * Coordinates geo_req throttling and in-flight tracking so map overlays can
 * request tiles without overwhelming the server.
 */
public final class GeoThrottle {
    private final long minIntervalNs;

    private boolean inFlight;
    private long lastSentNs;
    private long queuedGx = Long.MIN_VALUE;
    private long queuedGy = Long.MIN_VALUE;

    public GeoThrottle(long minIntervalNs) {
        this.minIntervalNs = minIntervalNs;
    }

    public synchronized boolean tryAcquire(long gx, long gy) {
        long now = System.nanoTime();
        if (inFlight) {
            queue(gx, gy);
            return false;
        }
        if (now - lastSentNs < minIntervalNs) {
            queue(gx, gy);
            return false;
        }
        inFlight = true;
        lastSentNs = now;
        return true;
    }

    public synchronized long[] releaseAndNext() {
        inFlight = false;
        return consumeQueuedIfReady();
    }

    public synchronized long[] tryConsumeQueued() {
        if (inFlight) {
            return null;
        }
        return consumeQueuedIfReady();
    }

    public synchronized void cancelInFlight() {
        inFlight = false;
    }

    private void queue(long gx, long gy) {
        queuedGx = gx;
        queuedGy = gy;
    }

    private long[] consumeQueuedIfReady() {
        if (queuedGx == Long.MIN_VALUE || queuedGy == Long.MIN_VALUE) {
            return null;
        }
        long now = System.nanoTime();
        if (now - lastSentNs < minIntervalNs) {
            return null;
        }
        long gx = queuedGx;
        long gy = queuedGy;
        queuedGx = Long.MIN_VALUE;
        queuedGy = Long.MIN_VALUE;
        inFlight = true;
        lastSentNs = now;
        return new long[]{gx, gy};
    }
}
