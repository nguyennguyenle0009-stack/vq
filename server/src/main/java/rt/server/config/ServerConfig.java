package rt.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {
    private final int port;
    private final int tps;
    private final int snapshotHz;

    private ServerConfig(int port, int tps, int snapshotHz) {
        this.port = port;
        this.tps = tps;
        this.snapshotHz = snapshotHz;
    }

    public static ServerConfig load() {
        Properties p = new Properties();
        try (InputStream in = ServerConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            // dùng default nếu đọc lỗi
        }
        int port = parseIntOrDefault(p.getProperty("server.port"), 8090);
        int tps = parseIntOrDefault(p.getProperty("server.tps"), 60);
        int hz  = parseIntOrDefault(p.getProperty("server.snapshot_hz"), 12);
        return new ServerConfig(port, tps, hz);
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    public int port() { return port; }
    public int tps() { return tps; }
    public int snapshotHz() { return snapshotHz; }

    @Override public String toString() {
        return "ServerConfig{port=" + port + ", tps=" + tps + ", snapshotHz=" + snapshotHz + "}";
    }
}
