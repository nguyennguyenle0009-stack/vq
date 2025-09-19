package rt.client.world;

import rt.common.net.Jsons;
import rt.common.world.atlas.AtlasMeta;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side loader cho world atlas TMS (HTTP tile). */
public final class WorldAtlasClient {
    private static final int MAX_TILE_CACHE = 200;
    private static final int TILE_PX = 32;
    private static final int MAX_OUTPUT = 512;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String baseHttp;
    private AtlasMeta meta;

    private final Map<TileKey, BufferedImage> tileCache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TileKey, BufferedImage> eldest) {
            if (size() > MAX_TILE_CACHE) {
                BufferedImage img = eldest.getValue();
                if (img != null) img.flush();
                return true;
            }
            return false;
        }
    };

    private BufferedImage composeBuffer;
    private int composeSize;

    public synchronized void configure(String wsUrl) {
        this.baseHttp = deriveHttpBase(wsUrl);
        this.meta = fetchMeta();
        clearTiles();
    }

    private void clearTiles() {
        tileCache.values().forEach(img -> { if (img != null) img.flush(); });
        tileCache.clear();
    }

    public synchronized boolean ready() { return meta != null; }

    public synchronized void invalidateByChunk(int cx, int cy) {
        if (meta == null) return;
        int chunkSize = rt.common.world.ChunkPos.SIZE;
        long minTileX = (long) cx * chunkSize;
        long minTileY = (long) cy * chunkSize;
        long maxTileX = minTileX + chunkSize;
        long maxTileY = minTileY + chunkSize;

        tileCache.entrySet().removeIf(entry -> {
            boolean match = overlaps(entry.getKey(), minTileX, minTileY, maxTileX, maxTileY);
            if (match) {
                BufferedImage img = entry.getValue();
                if (img != null) img.flush();
            }
            return match;
        });
    }

    private boolean overlaps(TileKey key, long minTileX, long minTileY, long maxTileX, long maxTileY) {
        if (meta == null) return false;
        AtlasMeta.Level level = levelByZ(key.z);
        if (level == null) return false;
        int tileSpan = level.tileSpanTiles(meta.tileSizePx());
        long originX = (long) meta.originTileX() + (long) key.x * tileSpan;
        long originY = (long) meta.originTileY() + (long) key.y * tileSpan;
        long endX = originX + tileSpan;
        long endY = originY + tileSpan;
        return minTileX < endX && maxTileX > originX && minTileY < endY && maxTileY > originY;
    }

    public synchronized BufferedImage composeRegion(double centerTileX, double centerTileY, int scaleWorldPx, int pixelSize) {
        if (meta == null) return null;
        pixelSize = Math.max(1, Math.min(MAX_OUTPUT, pixelSize));

        AtlasMeta.Level level = chooseLevel(scaleWorldPx);
        if (level == null) return null;

        int tileSpanWorldPx = level.tileSpanWorldPx(meta.tileSizePx(), meta.tilePixelSize());
        long atlasOriginWorldX = (long) meta.originTileX() * TILE_PX;
        long atlasOriginWorldY = (long) meta.originTileY() * TILE_PX;

        double spanWorldPx = pixelSize * (double) scaleWorldPx;
        double startWorldX = centerTileX * TILE_PX - spanWorldPx / 2.0;
        double startWorldY = centerTileY * TILE_PX - spanWorldPx / 2.0;
        double endWorldX = startWorldX + spanWorldPx;
        double endWorldY = startWorldY + spanWorldPx;

        int firstTileX = (int) Math.floor((startWorldX - atlasOriginWorldX) / tileSpanWorldPx);
        int lastTileX = (int) Math.floor((endWorldX - atlasOriginWorldX) / tileSpanWorldPx);
        int firstTileY = (int) Math.floor((startWorldY - atlasOriginWorldY) / tileSpanWorldPx);
        int lastTileY = (int) Math.floor((endWorldY - atlasOriginWorldY) / tileSpanWorldPx);

        firstTileX = Math.max(0, Math.min(firstTileX, level.tilesX() - 1));
        lastTileX = Math.max(0, Math.min(lastTileX, level.tilesX() - 1));
        firstTileY = Math.max(0, Math.min(firstTileY, level.tilesY() - 1));
        lastTileY = Math.max(0, Math.min(lastTileY, level.tilesY() - 1));

        BufferedImage out = ensureComposeBuffer(pixelSize);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setColor(new Color(0, 0, 0, 0));
            g2.fillRect(0, 0, pixelSize, pixelSize);

            for (int ty = firstTileY; ty <= lastTileY; ty++) {
                for (int tx = firstTileX; tx <= lastTileX; tx++) {
                    BufferedImage tileImg = loadTile(level.z(), tx, ty);
                    if (tileImg == null) continue;
                    long tileWorldX = atlasOriginWorldX + (long) tx * tileSpanWorldPx;
                    long tileWorldY = atlasOriginWorldY + (long) ty * tileSpanWorldPx;

                    double dx = (tileWorldX - startWorldX) / scaleWorldPx;
                    double dy = (tileWorldY - startWorldY) / scaleWorldPx;
                    double dw = tileSpanWorldPx / (double) scaleWorldPx;
                    double dh = tileSpanWorldPx / (double) scaleWorldPx;

                    AffineTransform at = AffineTransform.getTranslateInstance(dx, dy);
                    at.scale(dw / tileImg.getWidth(), dh / tileImg.getHeight());
                    g2.drawImage(tileImg, at, null);
                }
            }
        } finally {
            g2.dispose();
        }
        return out;
    }

    private BufferedImage ensureComposeBuffer(int size) {
        if (composeBuffer == null || composeSize != size) {
            if (composeBuffer != null) composeBuffer.flush();
            composeBuffer = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            composeSize = size;
        }
        return composeBuffer;
    }

    private AtlasMeta.Level chooseLevel(int scaleWorldPx) {
        if (meta == null || meta.levels() == null || meta.levels().length == 0) return null;
        AtlasMeta.Level best = meta.levels()[0];
        double bestDiff = Math.abs(worldPerPixel(best) - scaleWorldPx);
        for (AtlasMeta.Level level : meta.levels()) {
            double diff = Math.abs(worldPerPixel(level) - scaleWorldPx);
            if (diff < bestDiff) {
                best = level;
                bestDiff = diff;
            }
        }
        return best;
    }

    private double worldPerPixel(AtlasMeta.Level level) {
        double world = level.tileSpanWorldPx(meta.tileSizePx(), meta.tilePixelSize());
        return world / meta.tileSizePx();
    }

    private BufferedImage loadTile(int z, int x, int y) {
        TileKey key = new TileKey(z, x, y);
        BufferedImage cached = tileCache.get(key);
        if (cached != null) return cached;
        BufferedImage img = fetchTile(key);
        if (img != null) {
            tileCache.put(key, img);
        }
        return img;
    }

    private BufferedImage fetchTile(TileKey key) {
        if (baseHttp == null) return null;
        String url = baseHttp + "/atlas/" + key.z + "/" + key.x + "/" + key.y + ".png";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(5)).build();
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                resp.body().close();
                return null;
            }
            try (InputStream in = resp.body()) {
                return ImageIO.read(in);
            }
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private AtlasMeta.Level levelByZ(int z) {
        if (meta == null || meta.levels() == null) return null;
        for (AtlasMeta.Level level : meta.levels()) {
            if (level.z() == z) return level;
        }
        return null;
    }

    private AtlasMeta fetchMeta() {
        if (baseHttp == null) return null;
        String url = baseHttp + "/atlas/meta.json";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(5)).build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            return Jsons.OM.readValue(resp.body(), AtlasMeta.class);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String deriveHttpBase(String wsUrl) {
        if (wsUrl == null || wsUrl.isBlank()) return null;
        String trimmed = wsUrl.trim();
        if (trimmed.startsWith("ws://")) {
            return "http://" + trimmed.substring(5, trimmed.indexOf('/', 5) > 0 ? trimmed.indexOf('/', 5) : trimmed.length());
        }
        if (trimmed.startsWith("wss://")) {
            return "https://" + trimmed.substring(6, trimmed.indexOf('/', 6) > 0 ? trimmed.indexOf('/', 6) : trimmed.length());
        }
        int idx = trimmed.indexOf('/', trimmed.indexOf("://") + 3);
        if (idx > 0) return trimmed.substring(0, idx);
        return trimmed;
    }

    private record TileKey(int z, int x, int y) {}
}

