package rt.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ServerConfig {
    public int port = 8090;
    public int tps = 60;
    public int snapshotHz = 20;

    // WebSocket/pipeline
    public String wsPath = "/ws";
    public int wsMaxFrameKB = 64;      // maxFramePayloadLength
    public int idleSeconds = 45;
    public boolean wsAllowExtensions = false; // permessage-deflate
    public boolean checkOrigin = true;
    public List<String> allowedOrigins = List.of(
            "http://localhost:8080", 
            "http://127.0.0.1:8080"
    );

    // socket options
    public boolean tcpNoDelay = true;
    public boolean soKeepAlive = true;
    public int writeBufferLowKB = 32;
    public int writeBufferHighKB = 64;

    public String adminToken = "dev-secret-123";
    public String mapResourcePath = "/maps/test01.json";
    public String atlasDir = "atlas";

    public static final long DEFAULT_WORLD_SEED = 20250917L;

    public long worldSeed = DEFAULT_WORLD_SEED;

    public static ServerConfig load() {
        ObjectMapper om = new ObjectMapper();
        try {
            // ưu tiên file ở working dir
            Path p = Path.of("server-config.json");
            if (Files.exists(p)) return om.readValue(Files.newInputStream(p), ServerConfig.class);
            // fallback: resources
            try (InputStream in = ServerConfig.class.getResourceAsStream("/server-config.json")) {
                if (in != null) return om.readValue(in, ServerConfig.class);
            }
        } catch (Exception ignore) {}
        return new ServerConfig();
    }

    @Override public String toString() {
        return "ServerConfig{port=%d, tps=%d, snapshotHz=%d, wsPath=%s}".formatted(port,tps,snapshotHz,wsPath);
    }
}
