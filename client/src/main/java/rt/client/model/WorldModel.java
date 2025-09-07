package rt.client.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static rt.common.game.Units.*;

public class WorldModel {
    // ===== Interpolation buffer (đơn vị tile) =====
    public static final class Pos { public double x, y; }
    public static final class Snapshot {
        public final long ts; public final Map<String, Pos> ents;
        Snapshot(long ts, Map<String, Pos> ents){ this.ts = ts; this.ents = ents; }
    }
    private final Deque<Snapshot> buffer = new ArrayDeque<>();
    private static final int MAX_BUF = 60;
    private static final long INTERP_DELAY_MS = 100;

    // ===== Identity =====
    private volatile String you;
    public void setYou(String id){ this.you=id; } public String you(){ return you; }

    // ===== Prediction (tile) =====
    private volatile boolean hasPred=false; private volatile double predX, predY;

    // ===== Pending inputs =====
    public static final class Pending {
        public final int seq; public final long ts;
        public final boolean up,down,left,right;
        public Pending(int seq,long ts,boolean up,boolean down,boolean left,boolean right){
            this.seq=seq;this.ts=ts;this.up=up;this.down=down;this.left=left;this.right=right;}
    }
    private final Deque<Pending> pending = new ArrayDeque<>();

    /** Nhận state từ server (tile). */
    @SuppressWarnings("unchecked")
    public void applyState(Map<String,Object> root){
        Object entsObj = root.get("ents");
        Object tsObj = root.get("ts");
        if (!(entsObj instanceof Map) || !(tsObj instanceof Number)) return;
        long ts = ((Number)tsObj).longValue();

        Map<String,Object> em = (Map<String,Object>)entsObj;
        Map<String,Pos> ents = new HashMap<>(em.size());
        for (var e: em.entrySet()){
            Map<String,Object> xy = (Map<String,Object>) e.getValue();
            Pos p = new Pos();
            p.x = ((Number)xy.getOrDefault("x",0)).doubleValue();
            p.y = ((Number)xy.getOrDefault("y",0)).doubleValue();
            ents.put(e.getKey(), p);
        }
        synchronized (buffer){
            buffer.addLast(new Snapshot(ts, ents));
            while (buffer.size() > MAX_BUF) buffer.removeFirst();
        }
    }

    /** Lấy ảnh thế giới tại thời điểm render (tile). */
    public Map<String,Pos> sampleForRender(){
        long tRender = System.currentTimeMillis() - INTERP_DELAY_MS;
        Snapshot a=null,b=null;
        synchronized (buffer){
            if (buffer.isEmpty()) return new HashMap<>();
            for (Snapshot s: buffer){ if (s.ts <= tRender) a=s; if (s.ts >= tRender){ b=s; break; } }
            if (a==null) a=buffer.peekFirst(); if (b==null) b=buffer.peekLast();
        }
        if (a==null || b==null) return new HashMap<>();
        if (a==b) return new HashMap<>(a.ents);

        double t = (tRender - a.ts) / (double)(b.ts - a.ts);
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
        // đặt lại theo server (tile)
        predX = serverX; predY = serverY; hasPred = true;

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
}
