package rt.client.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import rt.common.net.dto.StateS2C;

import static rt.common.game.Units.*;


public class WorldModel {
	
	public Pos youPos() {
	    // ưu tiên ảnh chụp nội suy từ server
	    Map<String, Pos> snap = sampleForRender();
	    if (you != null) {
	        Pos p = snap.get(you);
	        if (p != null) return p;
	    }
	    // fallback: prediction nếu có
	    if (you != null && hasPred) {
	        Pos p = new Pos(); p.x = predX; p.y = predY; return p;
	    }
	    return null;
	}

	public double youX() { Pos p = youPos(); return p==null ? 0.0 : p.x; }
	public double youY() { Pos p = youPos(); return p==null ? 0.0 : p.y; }
	
    private volatile long lastTick = 0;
    public long lastTick(){ return lastTick; }

    private volatile MapModel map;
    public void setMap(MapModel m){ this.map = m; }
    public MapModel map(){ return map; }
    
    // ===== Interpolation buffer (đơn vị tile) =====
    public static final class Pos { public double x, y; }
    public static final class Snapshot {
        public final long ts; public final Map<String, Pos> ents;
        Snapshot(long ts, Map<String, Pos> ents){ this.ts = ts; this.ents = ents; }
    }
    private final Deque<Snapshot> buffer = new ArrayDeque<>();
    private static final int MAX_BUF = 60;

    // clock sync: t_server ≈ t_client + offset
    private volatile boolean hasOffset = false;
    private volatile double offsetMs = 0;           // ước lượng (EMA)
    private static final double OFFSET_ALPHA = 0.12; // mượt vừa phải
    
    // Ping EMA
    private volatile boolean hasPing = false;
    private static final double PING_ALPHA = 0.25;
    
    // render delay để nội suy (≥ 1 chu kỳ snapshot)
    private volatile long interpDelayMs = 100;      // sẽ tự tinh chỉnh nhẹ

    // ===== Identity =====
    private volatile String you;
    public void setYou(String id){ this.you=id; } public String you(){ return you; }

    // ===== Prediction (tile) =====
    private volatile boolean hasPred=false; 
    public volatile double predX, predY;

    // ===== Pending inputs =====
    public static final class Pending {
        public final int seq; public final long ts;
        public final boolean up,down,left,right;
        public Pending(int seq,long ts,boolean up,boolean down,boolean left,boolean right){
            this.seq=seq;this.ts=ts;this.up=up;this.down=down;this.left=left;this.right=right;}
    }
    private final Deque<Pending> pending = new ArrayDeque<>();
    
    // ===== Dev HUD metrics =====
    private volatile int devDroppedInputs, devStreamerSkips, devEntsServer;
    private volatile boolean devWritable;
    private volatile double pingMs = 0;

    public void setPingMs(double v){ pingMs = v; }
    public double pingMs(){ return pingMs; }
    public int devDroppedInputs(){ return devDroppedInputs; }
    public int devStreamerSkips(){ return devStreamerSkips; }
    public boolean devWritable(){ return devWritable; }
    public int devEntsServer(){ return devEntsServer; }
    public int pendingSize(){ return pending.size(); }
    public int lastAck(){ return pending.isEmpty()? 0 : pending.peekLast().seq; }
    public long renderTickEstimate(){
        synchronized (buffer){
            if (buffer.isEmpty()) return 0;
            // ước lượng tick theo chu kỳ snapshot ~20Hz (tuỳ config server)
            return buffer.peekLast().ts / 50L;
        }
    }
    public void applyDevStats(int droppedInputs, int streamerSkips, boolean writable, int entsServer){
        this.devDroppedInputs = droppedInputs;
        this.devStreamerSkips = streamerSkips;
        this.devWritable = writable;
        this.devEntsServer = entsServer;
    }
    
    /** Nhận state từ DTO (server → client). */
    public void applyStateDTO(StateS2C st){
        long tsServer = st.ts();
        // ---- clock sync (EMA)
        long now = System.currentTimeMillis();
        double sampleOffset = tsServer - now; // server - client
        if (!hasOffset) { offsetMs = sampleOffset; hasOffset = true; }
        else            { offsetMs = offsetMs + (sampleOffset - offsetMs)*OFFSET_ALPHA; }

        Map<String,Pos> ents = new HashMap<>();
        if (st.ents()!=null) {
            st.ents().forEach((id, es) -> {
                Pos p = new Pos(); p.x = es.x(); p.y = es.y();
                ents.put(id, p);
            });
        }

        synchronized (buffer){
            if (!buffer.isEmpty()) {
                long prev = buffer.peekLast().ts;
                long dt = Math.max(50, Math.min(200, tsServer - prev)); // clamp 50..200ms
                interpDelayMs = Math.round(interpDelayMs*0.85 + dt*0.15); // smooth
            }
            buffer.addLast(new Snapshot(tsServer, ents));
            while (buffer.size() > MAX_BUF) buffer.removeFirst();
        }
    }

    /** Nhận state từ server (tile) + đồng bộ clock + ước lượng chu kỳ snapshot. */
    @SuppressWarnings("unchecked")
    public void applyState(Map<String,Object> root){
        Object entsObj = root.get("ents");
        Object tsObj = root.get("ts");
        Object tickObj = root.get("tick");
        if (tickObj instanceof Number) lastTick = ((Number) tickObj).longValue();
        if (!(entsObj instanceof Map) || !(tsObj instanceof Number)) return;
        long tsServer = ((Number)tsObj).longValue();

        // ---- clock sync (EMA)
        long now = System.currentTimeMillis();
        double sampleOffset = tsServer - now;                // server - client
        if (!hasOffset) { offsetMs = sampleOffset; hasOffset = true; }
        else            { offsetMs = offsetMs + (sampleOffset - offsetMs)*OFFSET_ALPHA; }

        // ---- decode ents (tile)
        Map<String,Object> em = (Map<String,Object>)entsObj;
        Map<String,Pos> ents = new HashMap<>(em.size());
        for (var e: em.entrySet()){
            Map<String,Object> xy = (Map<String,Object>) e.getValue();
            Pos p = new Pos();
            p.x = ((Number)xy.getOrDefault("x",0)).doubleValue();
            p.y = ((Number)xy.getOrDefault("y",0)).doubleValue();
            ents.put(e.getKey(), p);
        }

        // ---- ước lượng chu kỳ snapshot (mượt hoá interpDelay)
        synchronized (buffer){
            if (!buffer.isEmpty()) {
                long prev = buffer.peekLast().ts;
                long dt = Math.max(50, Math.min(200, tsServer - prev)); // clamp 50..200ms
                interpDelayMs = Math.round(interpDelayMs*0.85 + dt*0.15); // smooth
            }
            buffer.addLast(new Snapshot(tsServer, ents));
            while (buffer.size() > MAX_BUF) buffer.removeFirst();
        }
    }

    /** Lấy ảnh thế giới tại "thời gian server" (nội suy theo tile). */
    public Map<String,Pos> sampleForRender(){
        if (!hasOffset || buffer.isEmpty()) return new HashMap<>();
        long nowClient = System.currentTimeMillis();
        long tRenderServer = nowClient + Math.round(offsetMs) - interpDelayMs;

        Snapshot a=null,b=null;
        synchronized (buffer){
            if (buffer.isEmpty()) return new HashMap<>();
            for (Snapshot s: buffer){ if (s.ts <= tRenderServer) a=s; if (s.ts >= tRenderServer){ b=s; break; } }
            if (a==null) a=buffer.peekFirst(); if (b==null) b=buffer.peekLast();
        }
        if (a==null || b==null) return new HashMap<>();
        if (a==b) return new HashMap<>(a.ents);

        double t = (tRenderServer - a.ts) / (double)(b.ts - a.ts);
        if (t<0) t=0; if (t>1) t=1;

        Map<String,Pos> out = new ConcurrentHashMap<>();
        Set<String> ids = new HashSet<>(); ids.addAll(a.ents.keySet()); ids.addAll(b.ents.keySet());
        for (String id: ids){
            Pos pa=a.ents.get(id), pb=b.ents.get(id); if (pa==null) pa=pb; if (pb==null) pb=pa; if (pa==null) continue;
            Pos p = new Pos(); p.x = pa.x + (pb.x - pa.x)*t; p.y = pa.y + (pb.y - pa.y)*t; out.put(id,p);
        }
        return out;
    }

    // ===== Prediction tick (tile) theo input hiện tại =====
    public synchronized void tickLocalPrediction(double dtSec, boolean up, boolean down, boolean left, boolean right){
        if (!hasPred || you==null) return;
        double vx = (right?1:0) - (left?1:0);
        double vy = (down ?1:0) - (up  ?1:0);
        double len = Math.hypot(vx, vy);
        if (len>0){ vx/=len; vy/=len; }
        predX += vx * SPEED_TILES_PER_SEC * dtSec;
        predY += vy * SPEED_TILES_PER_SEC * dtSec;
        if (predX<0) predX=0; if (predY<0) predY=0;
        if (predX>WORLD_W_TILES) predX=WORLD_W_TILES;
        if (predY>WORLD_H_TILES) predY=WORLD_H_TILES;
    }

    public Pos getPredictedYou(){
        if (!hasPred || you==null) return null;
        Pos p = new Pos(); p.x=predX; p.y=predY; return p;
    }

    // ===== Pending & reconciliation =====
    public synchronized void onInputSent(int seq, boolean up, boolean down, boolean left, boolean right, long tsMs){
        pending.addLast(new Pending(seq, tsMs, up,down,left,right));
        while (pending.size()>200) pending.removeFirst();
    }
    public synchronized void onAck(int ackSeq){
        while (!pending.isEmpty() && pending.peekFirst().seq <= ackSeq) pending.removeFirst();
    }
    public synchronized void reconcileFromServer(double serverX, double serverY, int ackSeq){
        if (you==null){ return; }

        // ---- "soft" reconcile: nếu sai số nhỏ, pha trộn; nếu lớn, snap
        double dx = serverX - predX, dy = serverY - predY;
        double err = Math.hypot(dx, dy);
        if (err < 0.25) { // < 1/4 tile: blend mượt
            double alpha = 0.35;
            predX = predX + dx*alpha;
            predY = predY + dy*alpha;
        } else {
            predX = serverX; predY = serverY;
        }
        hasPred = true;

        // ---- replay pending (> ack)
        if (pending.isEmpty()) return;
        List<Pending> list = new ArrayList<>(pending);
        list.removeIf(p -> p.seq <= ackSeq);
        if (list.isEmpty()) return;

        for (int i=0;i<list.size();i++){
            Pending cur = list.get(i);
            long tsA = cur.ts;
            long tsB = (i+1<list.size()) ? list.get(i+1).ts : tsA + 33;
            double dt = Math.max(0, (tsB - tsA) / 1000.0);

            double vx = (cur.right?1:0) - (cur.left?1:0);
            double vy = (cur.down ?1:0) - (cur.up  ?1:0);
            double len = Math.hypot(vx, vy);
            if (len>0){ vx/=len; vy/=len; }

            predX += vx * SPEED_TILES_PER_SEC * dt;
            predY += vy * SPEED_TILES_PER_SEC * dt;
        }
        if (predX<0) predX=0; if (predY<0) predY=0;
        if (predX>WORLD_W_TILES) predX=WORLD_W_TILES;
        if (predY>WORLD_H_TILES) predY=WORLD_H_TILES;
    }

    /** Gọi sau hello để có chấm ngay khi chưa về state. */
    public synchronized void spawnAt(double tileX, double tileY){
        hasPred=true; predX=tileX; predY=tileY;
        Map<String,Pos> ents = new HashMap<>();
        Pos p = new Pos(); p.x=tileX; p.y=tileY; ents.put(you, p);
        long now = System.currentTimeMillis();
        buffer.addLast(new Snapshot(now, ents));
        while (buffer.size() > MAX_BUF) buffer.removeFirst();
    }
    
    public void setPingMs(long rttMs) {
        double v = Math.max(0, rttMs);
        if (!hasPing) { pingMs = v; hasPing = true; }
        else          { pingMs = pingMs + (v - pingMs) * PING_ALPHA; }
    }
    public double pingText() { return hasPing ? (Math.round(pingMs)) : 0.0; }
    public int pingRoundedMs() { return (int)Math.round(pingMs); }
}
