package rt.client.gfx;

import rt.common.world.TerrainPalette;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class TerrainTextures {
    private TerrainTextures(){}

    private static final String BASE_DIR = "img/tile/sprite/"; // <== đổi theo bố cục mới

    // terrainId -> base name (không kèm folder/đuôi). Hiện tại để trống để giữ nguyên màu.
    private static final Map<Integer, String> BASE = new ConcurrentHashMap<>();
    private static final Map<Integer, List<BufferedImage>> ORIGINALS = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<Integer, List<BufferedImage>>> SCALED = new ConcurrentHashMap<>();

    /** Đăng ký hoặc gỡ đăng ký (baseName==null để gỡ) */
    public static void register(int terrainId, String baseName) {
        if (baseName == null || baseName.isBlank()) BASE.remove(terrainId);
        else BASE.put(terrainId, baseName);
        ORIGINALS.clear();
        SCALED.clear();
    }

    /** Lấy tile (sprite nếu có; nếu không → màu `TerrainPalette`). */
    public static BufferedImage getTile(int terrainId, int tileSize, int x, int y) {
        List<BufferedImage> variants = getScaledVariants(terrainId, tileSize);
        if (!variants.isEmpty()) {
            int idx = variantIndex(x, y, variants.size());
            return variants.get(idx);
        }
        // Fallback: màu từ common
        int argb = 0xFF808080;
        if (terrainId >= 0 && terrainId < TerrainPalette.ARGB.length) {
            int c = TerrainPalette.ARGB[terrainId];
            if (c != 0) argb = c;
        }
        BufferedImage out = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, tileSize, tileSize);
        g.dispose();
        return out;
    }

    // ===== helpers =====

    private static List<BufferedImage> getScaledVariants(int terrainId, int tileSize) {
        String base = BASE.get(terrainId);
        if (base == null) return Collections.emptyList();
        Map<Integer, List<BufferedImage>> perSize =
                SCALED.computeIfAbsent(terrainId, k -> new ConcurrentHashMap<>());
        return perSize.computeIfAbsent(tileSize, s -> {
            List<BufferedImage> srcs = loadOriginals(base);
            if (srcs.isEmpty()) return Collections.emptyList();
            ArrayList<BufferedImage> outs = new ArrayList<>(srcs.size());
            for (BufferedImage img : srcs) outs.add(scale(img, tileSize));
            return outs;
        });
    }

    private static List<BufferedImage> loadOriginals(String base) {
        return ORIGINALS.computeIfAbsent(base.hashCode(), k -> {
            ArrayList<BufferedImage> list = new ArrayList<>();
            loadOne(list, BASE_DIR + base + ".png");
            for (int i = 1; i <= 15; i++) loadOne(list, BASE_DIR + base + "_" + i + ".png");
            return list;
        });
    }

    private static void loadOne(List<BufferedImage> out, String path) {
        try (InputStream in = TerrainTextures.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) out.add(img);
            }
        } catch (Exception ignore) {}
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        if (src.getWidth() == size && src.getHeight() == size) return src;
        Image scaled = src.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return out;
    }

    private static int variantIndex(int x, int y, int n) {
        if (n <= 1) return 0;
        int h = 146959810 ^ (x * 73856093) ^ (y * 19349663);
        if (h < 0) h = -h;
        return h % n;
    }
}
