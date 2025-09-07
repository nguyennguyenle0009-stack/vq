package rt.common.util;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.nio.file.*;

public final class DesktopDir {
    private DesktopDir() {}

    /** Trả về thư mục Desktop thật (có thể là OneDrive). Fallback: user.home */
    public static Path resolve() {
        try {
            File f = FileSystemView.getFileSystemView().getHomeDirectory();
            if (f != null && f.exists()) return f.toPath();
            String user = System.getenv("USERPROFILE");
            if (user != null) {
                Path p1 = Path.of(user, "Desktop");
                if (Files.isDirectory(p1)) return p1;
                Path p2 = Path.of(user, "OneDrive", "Desktop");
                if (Files.isDirectory(p2)) return p2;
            }
        } catch (Throwable ignore) {}
        return Path.of(System.getProperty("user.home"));
    }
}

