package rt.client.ui.minimap;

import rt.client.world.ChunkCache;
import rt.client.world.WorldLookup;
import rt.common.world.BiomeId;
import rt.common.world.OverlayId;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renderer minimap địa phương: vẽ vùng quanh người chơi và downsample. */
public final class LocalMinimapRenderer {
    private static final int TILE_PX = 32;
    private static final int MAX_CACHE = 8;
    private static final int MAX_RAW = 8192;

    private WorldLookup lookup;
    private ChunkCache chunkCache;

    private BufferedImage rawBuffer;
    private int rawSize;

    private final Map<Key, Entry> cache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
            if (size() > MAX_CACHE) {
                Entry e = eldest.getValue();
                if (e != null && e.image != null) e.image.flush();
                return true;
            }
            return false;
        }
    };

    public void setLookup(WorldLookup lookup) { this.lookup = lookup; }
    public void setChunkCache(ChunkCache cache) { this.chunkCache = cache; }

    public synchronized BufferedImage renderMini(double centerTileX, double centerTileY, int radiusTiles, int scaleWorldPx) {
        int spanTiles = Math.max(1, radiusTiles * 2);
        double spanWorldPx = spanTiles * TILE_PX;
        int pixelSize = Math.max(16, Math.min(256, (int) Math.ceil(spanWorldPx / scaleWorldPx)));

        long epoch = chunkCache != null ? chunkCache.epoch() : 0L;
        long qx = Math.round(centerTileX * 4.0);
        long qy = Math.round(centerTileY * 4.0);
        Key key = new Key(qx, qy, spanTiles, scaleWorldPx, pixelSize);
        Entry entry = cache.get(key);
        if (entry != null && entry.epoch == epoch) {
            return entry.image;
        }

        BufferedImage target;
        if (entry == null || entry.image.getWidth() != pixelSize) {
            if (entry != null && entry.image != null) entry.image.flush();
            target = new BufferedImage(pixelSize, pixelSize, BufferedImage.TYPE_INT_ARGB);
            entry = new Entry(target, 0L);
        } else {
            target = entry.image;
        }

        renderInto(target, centerTileX, centerTileY, scaleWorldPx, spanWorldPx);
        entry.epoch = epoch;
        cache.put(key, entry);
        return target;
    }

    private void renderInto(BufferedImage target, double centerTileX, double centerTileY, int scaleWorldPx, double spanWorldPx) {
        int pixelSize = target.getWidth();
        double startWorldX = centerTileX * TILE_PX - spanWorldPx / 2.0;
        double startWorldY = centerTileY * TILE_PX - spanWorldPx / 2.0;

        int raw = Math.max(32, Math.min(MAX_RAW, (int) Math.ceil(spanWorldPx)));
        BufferedImage rawImg = ensureRawBuffer(raw);
        paintTerrain(rawImg, startWorldX, startWorldY, raw);

        Graphics2D g2 = target.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(rawImg, 0, 0, pixelSize, pixelSize, null);
        } finally {
            g2.dispose();
        }
    }

    private BufferedImage ensureRawBuffer(int size) {
        if (rawBuffer == null || rawSize != size) {
            if (rawBuffer != null) rawBuffer.flush();
            rawBuffer = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            rawSize = size;
        }
        return rawBuffer;
    }

    private void paintTerrain(BufferedImage rawImg, double startWorldX, double startWorldY, int raw) {
        int[] pixels = ((DataBufferInt) rawImg.getRaster().getDataBuffer()).getData();
        double endWorldX = startWorldX + raw;
        double endWorldY = startWorldY + raw;

        long firstTileX = (long) Math.floor(startWorldX / TILE_PX);
        long lastTileX = (long) Math.floor((endWorldX - 1) / TILE_PX);
        long firstTileY = (long) Math.floor(startWorldY / TILE_PX);
        long lastTileY = (long) Math.floor((endWorldY - 1) / TILE_PX);

        for (long ty = firstTileY; ty <= lastTileY; ty++) {
            double tileMinY = ty * TILE_PX;
            double tileMaxY = tileMinY + TILE_PX;
            int y0 = clamp((int) Math.floor(tileMinY - startWorldY), 0, raw);
            int y1 = clamp((int) Math.ceil(tileMaxY - startWorldY), y0, raw);

            for (long tx = firstTileX; tx <= lastTileX; tx++) {
                double tileMinX = tx * TILE_PX;
                double tileMaxX = tileMinX + TILE_PX;
                int x0 = clamp((int) Math.floor(tileMinX - startWorldX), 0, raw);
                int x1 = clamp((int) Math.ceil(tileMaxX - startWorldX), x0, raw);

                int color = sampleColor(tx, ty);
                if (color == 0) continue;
                for (int y = y0; y < y1; y++) {
                    int row = y * raw;
                    java.util.Arrays.fill(pixels, row + x0, row + x1, color);
                }
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int sampleColor(long tileX, long tileY) {
        if (chunkCache != null) {
            int size = rt.common.world.ChunkPos.SIZE;
            int cx = Math.floorDiv((int) tileX, size);
            int cy = Math.floorDiv((int) tileY, size);
            int lx = Math.floorMod((int) tileX, size);
            int ly = Math.floorMod((int) tileY, size);
            ChunkCache.Data data = chunkCache.get(cx, cy);
            if (data != null) {
                int idx = ly * data.size + lx;
                int overlay = data.l2[idx] & 0xFF;
                if (overlay != 0) {
                    int oc = colorForOverlay(overlay);
                    if (oc != 0) return oc;
                }
                int base = data.l1[idx] & 0xFF;
                return colorForBase(base);
            }
        }
        if (lookup != null && lookup.ready()) {
            int base = lookup.baseId(tileX, tileY);
            return colorForBase(base);
        }
        return 0xFF7F00FF;
    }

    private static int colorForBase(int id) {
        return switch (id) {
            case BiomeId.OCEAN -> 0xFF083B83;
            case BiomeId.LAND -> 0xFF556B2F;
            case BiomeId.PLAIN -> 0xFF3DA940;
            case BiomeId.PLAIN_WEIRD -> 0xFF66CDAA;
            case BiomeId.DESERT -> 0xFFE1C16E;
            case BiomeId.FOREST -> 0xFF196C2E;
            case BiomeId.FOREST_FOG -> 0xFF0F4A2C;
            case BiomeId.FOREST_MAGIC -> 0xFF2F4F7F;
            case BiomeId.FOREST_WEIRD -> 0xFF4F2F7F;
            case BiomeId.FOREST_DARK -> 0xFF061E15;
            case BiomeId.LAKE -> 0xFF1D5DBE;
            case BiomeId.RIVER -> 0xFF4F90E3;
            case BiomeId.MOUNTAIN_SNOW -> 0xFFF5F8FF;
            case BiomeId.MOUNTAIN_VOLCANO -> 0xFFB7410E;
            case BiomeId.MOUNTAIN_FOREST -> 0xFF4A5D23;
            case BiomeId.MOUNTAIN_ROCK -> 0xFF8B8680;
            case BiomeId.VILLAGE -> 0xFFCD2F2F;
            default -> 0xFF646464;
        };
    }

    private static int colorForOverlay(int id) {
        return switch (id) {
            case OverlayId.HOUSE -> 0xFF6E3B1B;
            case OverlayId.ROAD -> 0xFFB39C7F;
            case OverlayId.FARM -> 0xFFA3C178;
            default -> 0;
        };
    }

    private record Key(long qx, long qy, int spanTiles, int scale, int size) {}

    private static final class Entry {
        final BufferedImage image;
        long epoch;
        Entry(BufferedImage image, long epoch) { this.image = image; this.epoch = epoch; }
    }
}

