package rt.common.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static java.nio.file.StandardOpenOption.*;

public final class LogFiles {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
    private LogFiles() {}

    /** Ghi event riêng cho CLIENT, trả về đường dẫn file đã ghi. */
    public static Path writeClientEvent(String playerName, String event, String details) {
        try {
            Path base = DesktopDir.resolve().resolve("Vương quyền").resolve("client").resolve(safe(playerName));
            Files.createDirectories(base);
            String stamp = LocalDateTime.now().format(TS);
            String file = safe(playerName) + " log " + safe(event) + " " + stamp + ".txt";
            Path path = base.resolve(file);
            String line = stamp + " | " + playerName + " | " + event + " | " + details + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8, CREATE, APPEND);
            return path;
        } catch (Exception ignore) { return null; }
    }

    /** Ghi event riêng cho SERVER. */
    public static Path writeServerEvent(String event, String details) {
        try {
            Path base = DesktopDir.resolve().resolve("Vương quyền").resolve("server");
            Files.createDirectories(base);
            String stamp = LocalDateTime.now().format(TS);
            String file = "server log " + safe(event) + " " + stamp + ".txt";
            Path path = base.resolve(file);
            String line = stamp + " | " + event + " | " + details + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8, CREATE, APPEND);
            return path;
        } catch (Exception ignore) { return null; }
    }

    private static String safe(String s) {
        return (s == null ? "unknown" : s).replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    // 	thêm server khi có lỗi đặc biệt:
    //	rt.common.util.LogFiles.writeServerEvent("unhandled exception", cause.toString());
}
