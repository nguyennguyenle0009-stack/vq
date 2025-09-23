package rt.server.world;

import rt.common.net.dto.EntityState;
import rt.common.net.dto.StateS2C;
import rt.server.session.SessionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** World CHUNK-ONLY: không còn TileMap. Collision đọc từ ChunkService. */
public class World {
    private final SessionRegistry sessions;

    private static final class P { double x, y; }
    private final Map<String,P> players = new ConcurrentHashMap<>();
    private static final class Dir { final double x,y; Dir(double x,double y){this.x=x;this.y=y;} }
    private final Map<String,Dir> lastInput = new ConcurrentHashMap<>();
    private static final double SPEED = rt.common.game.Units.SPEED_TILES_PER_SEC;

    // ===== CHUNK =====
    private static final int CHUNK_SIZE = rt.common.world.ChunkPos.SIZE;
    private rt.server.world.chunk.ChunkService chunkService;

    // spawn mặc định (tìm 1 lần trên đất liền, cache lại)
    private volatile boolean spawnReady = false;
    private volatile double spawnX = -9247, spawnY = -631;

    // ==== tham số tìm spawn theo ô macro của lục địa ====
    private static final int CONT_CELL_TILES   = 12_000;                                  // khớp WorldGenerator
    private static final int MACRO_STEP_CHUNKS = Math.max(2, CONT_CELL_TILES / CHUNK_SIZE);
    private static final int FINE_STEP_CHUNKS  = 8;                                       // quét tinh
    private static final int MAX_MACRO_RINGS   = 64;                                      // 64*12000 ~ 768k tiles
    private static final int MAX_FINE_RADIUS   = 2048;                                    // 2048*64 ~ 131k tiles

    public World(SessionRegistry sessions){ this.sessions = sessions; }

    /** Bật chế độ chunk, gọi khi server khởi động. */
    public void enableChunkMode(rt.server.world.chunk.ChunkService svc){
        this.chunkService = Objects.requireNonNull(svc, "chunkService");
        try { computeDefaultSpawn(); } catch (Exception ignore) {}
    }
    public void setOverworldParams(long seed, int tileSize){}

    public void prewarmSpawnArea(int radiusChunks) {
        if (chunkService == null || radiusChunks < 0) return;
        ensureSpawnComputed();
        int baseCx = (int) Math.floor(spawnX / CHUNK_SIZE);
        int baseCy = (int) Math.floor(spawnY / CHUNK_SIZE);
        for (int dy = -radiusChunks; dy <= radiusChunks; dy++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                chunkService.get(baseCx + dx, baseCy + dy);
            }
        }
    }

    private void computeDefaultSpawn() {
        if (spawnReady || chunkService == null) return;

        // 1) Quét theo "ô macro" – đi từng vành
        for (int ring = 0; ring <= MAX_MACRO_RINGS; ring++) {
            int r = ring * MACRO_STEP_CHUNKS;
            if (probeRing(r, MACRO_STEP_CHUNKS)) { spawnReady = true; return; }
        }

        // 2) Rơi về quét tinh STEP=8 chunk
        for (int r = FINE_STEP_CHUNKS; r <= MAX_FINE_RADIUS; r += FINE_STEP_CHUNKS) {
            if (probeRing(r, FINE_STEP_CHUNKS)) { spawnReady = true; return; }
        }

        // 3) Không thấy thì giữ (3,3) (rất khó xảy ra nếu generator OK)
        spawnReady = true;
    }

    /** Dò “vành” hình vuông ở bán kính r (chunk), chỉ đi trên 4 cạnh. */
    private boolean probeRing(int r, int step) {
        if (r == 0) return scanChunkForFree(0, 0);
        for (int cy = -r; cy <= r; cy += step) {
            if (scanChunkForFree(-r, cy)) return true;
            if (scanChunkForFree( r, cy)) return true;
        }
        for (int cx = -r + step; cx <= r - step; cx += step) {
            if (scanChunkForFree(cx, -r)) return true;
            if (scanChunkForFree(cx,  r)) return true;
        }
        return false;
    }

    private boolean scanChunkForFree(int cx, int cy){
        var cd = chunkService.get(cx, cy); // sinh/gỡ từ cache
        int N = cd.size;
        for (int ty = 0; ty < N; ty++) {
            for (int tx = 0; tx < N; tx++) {
                int idx = ty * N + tx;
                if (!cd.collision.get(idx)) {
                    // chọn tâm ô cho đẹp
                    spawnX = cx * (double)N + tx + 0.5;
                    spawnY = cy * (double)N + ty + 0.5;
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureSpawnComputed() {
        if (!spawnReady) computeDefaultSpawn();
    }

    private P ensure(String id){
        ensureSpawnComputed();
        return players.computeIfAbsent(id, k -> { P p=new P(); p.x=spawnX; p.y=spawnY; return p; });
    }

    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right){
        double vx=(right?1:0)-(left?1:0), vy=(down?1:0)-(up?1:0);
        double len=Math.hypot(vx,vy); if (len>0){vx/=len;vy/=len;}
        lastInput.put(playerId,new Dir(vx,vy)); ensure(playerId);
    }

    private boolean isBlocked(int tx, int ty){
        if (chunkService == null) return false;
        int cx = Math.floorDiv(tx, CHUNK_SIZE),  cy = Math.floorDiv(ty, CHUNK_SIZE);
        int lx = Math.floorMod(tx, CHUNK_SIZE),  ly = Math.floorMod(ty, CHUNK_SIZE);
        var cd = chunkService.get(cx, cy);
        return cd.collision.get(ly * CHUNK_SIZE + lx);
    }

    public void step(double dt){
        lastInput.forEach((id,dir)->{
            P p = ensure(id);
            double nx = p.x + dir.x * SPEED * dt;
            double ny = p.y + dir.y * SPEED * dt;

            // Sweep X
            int tx = (int)Math.floor(nx), ty = (int)Math.floor(p.y);
            if (!isBlocked(tx,ty)) p.x = nx;
            // Sweep Y
            tx = (int)Math.floor(p.x); ty = (int)Math.floor(ny);
            if (!isBlocked(tx,ty)) p.y = ny;

            for (var s : sessions.all()){
                if (s.playerId.equals(id)){ s.x=p.x; s.y=p.y; break; }
            }
        });
    }

    public StateS2C capture(long tick){
        Map<String,EntityState> ents = new HashMap<>(players.size());
        players.forEach((id,p)->ents.put(id,new EntityState(p.x,p.y)));
        return new StateS2C(tick,System.currentTimeMillis(),ents);
    }

    public void copyForNetwork(Map<String,Map<String,Object>> out){
        players.forEach((id,p)->out.put(id, Map.of("x",p.x,"y",p.y)));
    }

    public double[] pos(String id){ P p=players.get(id); return p==null? null : new double[]{p.x,p.y}; }
    public void removePlayer(String id){ if(id==null)return; players.remove(id); lastInput.remove(id); }

    public boolean teleport(String id,double x,double y){
        if (isBlocked((int)Math.floor(x),(int)Math.floor(y))) return false;
        P p=ensure(id); p.x=x; p.y=y;
        for (var s: sessions.all()) if (s.playerId.equals(id)) { s.x=x; s.y=y; break; }
        return true;
    }

    public boolean reloadMap(String resourcePath){ return false; } // chunk-only
}
