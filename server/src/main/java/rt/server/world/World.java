package rt.server.world;

import rt.common.net.dto.EntityState;
import rt.common.net.dto.StateS2C;
import rt.server.session.SessionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    private final SessionRegistry sessions;

    // Player state
    private static final class P { double x, y; }
    private final Map<String,P> players = new ConcurrentHashMap<>();
    private static final class Dir { final double x,y; Dir(double x,double y){this.x=x;this.y=y;} }
    private final Map<String,Dir> lastInput = new ConcurrentHashMap<>();
    private static final double SPEED = 3.0;

    // ===== CHUNK MODE =====
    private boolean chunkMode = false;
    private rt.server.world.chunk.ChunkService chunkService = null;
    private final int CHUNK_SIZE = rt.common.world.ChunkPos.SIZE;

    // (Giữ TileMap để sau này dùng dungeon, NHƯNG không động tới khi chunkMode=true)
    private volatile TileMap map = null;
    public void setMap(TileMap m){ this.map = Objects.requireNonNull(m); }
    public TileMap map(){ return map; }

    public World(SessionRegistry sessions){ this.sessions = sessions; }

    // gọi từ WsTextHandler
    public void enableChunkMode(rt.server.world.chunk.ChunkService svc){
        this.chunkMode = true; this.chunkService = svc;
    }
    public void setOverworldParams(long seed, int tileSize){ /* để dành nếu client/hud cần */ }

    private P ensure(String id){
        return players.computeIfAbsent(id, k -> { P p=new P(); p.x=3; p.y=3; return p; });
    }

    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right){
        double vx=(right?1:0)-(left?1:0), vy=(down?1:0)-(up?1:0);
        double len=Math.hypot(vx,vy); if (len>0){vx/=len;vy/=len;}
        lastInput.put(playerId,new Dir(vx,vy)); ensure(playerId);
    }

    // check từ collision BitSet trong chunk
    private boolean blockedChunk(int tx, int ty){
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

            if (chunkMode && chunkService!=null){
                // 👉 CHUNK: không clamp biên, sweep X rồi Y bằng collision của chunk
                int tx = (int)Math.floor(nx), ty = (int)Math.floor(p.y);
                if (!blockedChunk(tx,ty)) p.x = nx;
                tx = (int)Math.floor(p.x); ty = (int)Math.floor(ny);
                if (!blockedChunk(tx,ty)) p.y = ny;
            } else {
                // Fallback TileMap (chỉ dùng nếu chưa bật chunkMode)
                TileMap m = this.map;
                if (m == null){ p.x = nx; p.y = ny; }
                else{
                    nx = Math.max(0, Math.min(nx, m.w - 1e-3));
                    ny = Math.max(0, Math.min(ny, m.h - 1e-3));
                    int tx = (int)Math.floor(nx), ty=(int)Math.floor(p.y);
                    if (!m.blocked(tx,ty)) p.x = nx;
                    tx = (int)Math.floor(p.x); ty=(int)Math.floor(ny);
                    if (!m.blocked(tx,ty)) p.y = ny;
                }
            }

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
        if (!(chunkMode && chunkService!=null)){
            TileMap m=this.map;
            if(m!=null){
                if (x<0||y<0||x>=m.w||y>=m.h) return false;
                int tx=(int)Math.floor(x), ty=(int)Math.floor(y);
                if (m.blocked(tx,ty)) return false;
            }
        }
        P p=ensure(id); p.x=x; p.y=y;
        sessions.all().forEach(s->{ if(s.playerId.equals(id)){ s.x=x; s.y=y; }});
        return true;
    }

    public boolean reloadMap(String resourcePath){
        try{ setMap(TileMap.loadResource(resourcePath)); return true; }
        catch(Exception e){ return false; }
    }
}
