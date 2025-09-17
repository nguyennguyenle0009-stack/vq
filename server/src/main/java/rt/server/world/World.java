package rt.server.world;

import rt.common.net.dto.EntityState;
import rt.common.net.dto.StateS2C;
import rt.server.session.SessionRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** World CHUNK-ONLY: không còn dùng TileMap tạm.
 *  Va chạm đọc từ BitSet của ChunkService; hỗ trợ toạ độ âm/dương (vô hạn).
 */
public class World {
    private final SessionRegistry sessions;

    // Player state
    private static final class P { double x, y; }
    private final Map<String,P> players = new ConcurrentHashMap<>();
    private static final class Dir { final double x,y; Dir(double x,double y){this.x=x;this.y=y;} }
    private final Map<String,Dir> lastInput = new ConcurrentHashMap<>();
    private static final double SPEED = 3.0;

    // ===== CHUNK =====
    private static final int CHUNK_SIZE = rt.common.world.ChunkPos.SIZE;
    private rt.server.world.chunk.ChunkService chunkService; // được set khi khởi tạo WsTextHandler

    public World(SessionRegistry sessions){ this.sessions = sessions; }

    /** Bắt buộc gọi khi server khởi động để bật chế độ chunk. */
    public void enableChunkMode(rt.server.world.chunk.ChunkService svc){
        this.chunkService = Objects.requireNonNull(svc, "chunkService");
    }
    public void setOverworldParams(long seed, int tileSize){ /* reserved for HUD/metrics */ }

    private P ensure(String id){
        return players.computeIfAbsent(id, k -> { P p=new P(); p.x=3; p.y=3; return p; });
    }

    public void applyInput(String playerId, boolean up, boolean down, boolean left, boolean right){
        double vx=(right?1:0)-(left?1:0), vy=(down?1:0)-(up?1:0);
        double len=Math.hypot(vx,vy); if (len>0){vx/=len;vy/=len;}
        lastInput.put(playerId,new Dir(vx,vy)); ensure(playerId);
    }

    // Va chạm theo BitSet trong chunk (an toàn với toạ độ âm)
    private boolean isBlocked(int tx, int ty){
        if (chunkService == null) return false;                  // chưa bật chunk -> free
        int cx = Math.floorDiv(tx, CHUNK_SIZE),  cy = Math.floorDiv(ty, CHUNK_SIZE);
        int lx = Math.floorMod(tx, CHUNK_SIZE),  ly = Math.floorMod(ty, CHUNK_SIZE);
        var cd = chunkService.get(cx, cy);
        return cd.collision.get(ly * CHUNK_SIZE + lx);
    }

    /** 1 tick logic: sweep X rồi Y, không clamp biên. */
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

            // phản chiếu sang session (để streamer đọc)
            for (var s : sessions.all()){
                if (s.playerId.equals(id)){ s.x=p.x; s.y=p.y; break; }
            }
        });
    }

    /** Ảnh chụp gửi cho client (đơn vị tile). */
    public StateS2C capture(long tick){
        Map<String,EntityState> ents = new HashMap<>(players.size());
        players.forEach((id,p)->ents.put(id,new EntityState(p.x,p.y)));
        return new StateS2C(tick,System.currentTimeMillis(),ents);
    }

    // (giữ nếu nơi khác đang dùng để debug)
    public void copyForNetwork(Map<String,Map<String,Object>> out){
        players.forEach((id,p)->out.put(id, Map.of("x",p.x,"y",p.y)));
    }

    public double[] pos(String id){ P p=players.get(id); return p==null? null : new double[]{p.x,p.y}; }
    public void removePlayer(String id){ if(id==null)return; players.remove(id); lastInput.remove(id); }

    /** Teleport hợp lệ nếu đích không bị chặn theo chunk. */
    public boolean teleport(String id,double x,double y){
        if (isBlocked((int)Math.floor(x),(int)Math.floor(y))) return false;
        P p=ensure(id); p.x=x; p.y=y;
        for (var s: sessions.all()) if (s.playerId.equals(id)) { s.x=x; s.y=y; break; }
        return true;
    }

    /** Giữ để tương thích API cũ; chunk-only nên luôn false. */
    public boolean reloadMap(String resourcePath){ return false; }
}
