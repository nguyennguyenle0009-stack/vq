package rt.client.app.config;

import rt.common.util.DesktopDir;

import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable bootstrap configuration for the Swing client. */
public record ClientConfiguration(String serverUrl,
                                  String playerName,
                                  String adminToken,
                                  Path logDirectory) {

    public static final String DEFAULT_ADMIN_TOKEN = "dev-secret-123";
    private static final String DEFAULT_URL = "ws://localhost:8090/ws";

    public static ClientConfiguration fromArgs(String[] args) {
        String playerName = args.length > 0 ? args[0] : "Player";
        String serverUrl = System.getProperty("vq.serverUrl", DEFAULT_URL);
        Path logDir = DesktopDir.resolve()
                .resolve("Vương quyền")
                .resolve("client")
                .resolve(playerName);
        createIfMissing(logDir);
        return new ClientConfiguration(serverUrl, playerName, DEFAULT_ADMIN_TOKEN, logDir);
    }

    private static void createIfMissing(Path dir) {
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
    }
}
