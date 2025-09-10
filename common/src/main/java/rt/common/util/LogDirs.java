package rt.common.util;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class LogDirs {
    private LogDirs() {}

    /** Tìm thư mục Desktop (Windows/macOS/Linux) và tạo nếu chưa có. */
    public static Path resolveDesktop() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", "");
        String userProfile = System.getenv("USERPROFILE");

        List<Path> candidates = new ArrayList<>();
        if (os.contains("win")) {
            if (userProfile != null && !userProfile.isBlank()) {
                candidates.add(Paths.get(userProfile, "Desktop"));
                candidates.add(Paths.get(userProfile, "OneDrive", "Desktop")); // OneDrive
            }
            if (home != null && !home.isBlank()) {
                candidates.add(Paths.get(home, "Desktop"));
                candidates.add(Paths.get(home, "OneDrive", "Desktop"));
            }
            try {
                File f = FileSystemView.getFileSystemView().getHomeDirectory();
                if (f != null) candidates.add(f.toPath());
            } catch (Throwable ignored) {}
        } else if (os.contains("mac")) {
            if (!home.isBlank()) candidates.add(Paths.get(home, "Desktop"));
        } else {
            // Linux: thử xdg-user-dir DESKTOP
            try {
                Process p = new ProcessBuilder("xdg-user-dir", "DESKTOP").redirectErrorStream(true).start();
                byte[] out = p.getInputStream().readAllBytes();
                String xdg = new String(out, StandardCharsets.UTF_8).trim();
                if (!xdg.isBlank()) candidates.add(Paths.get(xdg));
            } catch (Throwable ignored) {}
            if (!home.isBlank()) candidates.add(Paths.get(home, "Desktop"));
        }

        for (Path c : candidates) {
            if (c == null) continue;
            try { Files.createDirectories(c); return c; } catch (Throwable ignored) {}
        }
        Path fb = home.isBlank() ? Paths.get(".") : Paths.get(home);
        try { Files.createDirectories(fb); } catch (Throwable ignored) {}
        return fb;
    }

    /** Tạo .../Desktop/<folder1>/<folder2> nếu chưa có và set system property cho logback. */
    public static Path ensureDesktopSubdir(String folder1, String folder2, String sysProp) {
        Path dir = resolveDesktop().resolve(folder1).resolve(folder2);
        try { Files.createDirectories(dir); } catch (Throwable ignored) {}
        if (sysProp != null && !sysProp.isBlank()) {
            System.setProperty(sysProp, dir.toString());
        }
        return dir;
    }
}
