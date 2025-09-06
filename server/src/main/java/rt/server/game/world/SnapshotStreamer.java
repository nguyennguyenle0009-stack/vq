package rt.server.game.world;

import rt.server.session.SessionRegistry;
import vq.common.Packets;

/** Gửi state ra client theo tần số thấp hơn (ví dụ 12 Hz) để tiết kiệm băng thông. */
public class SnapshotStreamer implements Runnable {
    private final SessionRegistry sessions; private final SnapshotBuffer snaps; private final int hz;
    public SnapshotStreamer(SessionRegistry s, SnapshotBuffer b, int hz){ this.sessions=s; this.snaps=b; this.hz=hz; }

    @Override public void run() {
        long interval = 1000L / hz;
        while (true){
            var snap = snaps.latest();
            for (var s : sessions.all()){
                var pkt = new Packets.S2CState();
                pkt.op   = "state";
                pkt.tick = snap.tick();
                pkt.ents = snap.ents();
                // "you" cho người này
                var me = new Packets.S2CState.Player();
                me.x = s.x; me.y = s.y; pkt.you = me;
                s.send(pkt);
            }
            try { Thread.sleep(interval); } catch (InterruptedException ignored) {}
        }
    }
}