package rt.client.world.map;

import rt.client.world.ChunkBaker;
import rt.client.world.ChunkCache;
import rt.common.world.ChunkPos;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Vẽ bản đồ (world map/minimap) từ ChunkCache có sẵn ở client.
 * - Dùng ChunkBaker để đảm bảo có ảnh chunk (sprite nếu có; thiếu ảnh -> màu palette).
 * - Không đụng tới server/common.
 */
public final class MapRenderer {

    /** Cache dùng để render. Phải gán một lần khi app khởi động. */
    private static ChunkCache STATIC_CACHE;

    /** GÁN CACHE TỪ APP: gọi 1 lần lúc khởi động client. */
    public static void setCache(ChunkCache cache) { STATIC_CACHE = cache; }

    private final int N = ChunkPos.SIZE; // số tile mỗi chunk

    // Giữ chữ ký cũ cho WorldMapOverlay.setWorldGenConfig(...)
    public MapRenderer(rt.common.world.WorldGenConfig cfg) { }

    /** Lấy cache – chỉnh nếu bạn để cache ở singleton khác. */
    private ChunkCache getCache() { return STATIC_CACHE; }

    /**
     * @param originX,originY gốc (tính theo TILE) của khung nhìn
     * @param tpp             tilesPerPixel (ví dụ 1: 1px = 1 tile; 4: 1px = 4 tiles)
     * @param w,h             kích thước ảnh xuất (pixel)
     */
    public BufferedImage render(long originX, long originY, double tpp, int w, int h) {
        ChunkCache cache = getCache();
        if (cache == null || w <= 0 || h <= 0) {
            return new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0x121314));
        g.fillRect(0, 0, w, h);

        // vẽ ở hệ toạ độ "tile-pixel" rồi scale về w,h bằng tpp
        AffineTransform bak = g.getTransform();
        g.scale(1.0 / tpp, 1.0 / tpp);

        long visTilesW = Math.round(w * tpp);
        long visTilesH = Math.round(h * tpp);

        int cx0 = Math.floorDiv((int) originX, N) - 1;
        int cy0 = Math.floorDiv((int) originY, N) - 1;
        int cx1 = Math.floorDiv((int) (originX + visTilesW), N) + 1;
        int cy1 = Math.floorDiv((int) (originY + visTilesH), N) + 1;

        final int bakeTileSize = 1; // 1 tile = 1 "px" tạm
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                ChunkCache.Data d = cache.get(cx, cy);
                if (d == null) continue;

                ChunkBaker.bake(d, bakeTileSize);

                long chunkTileX = (long) cx * N;
                long chunkTileY = (long) cy * N;
                int dx = (int) (chunkTileX - originX);
                int dy = (int) (chunkTileY - originY);

                g.drawImage(d.img, dx, dy, null);
            }
        }

        g.setTransform(bak);
        g.dispose();
        return out;
    }
}
