package rt.server.game.loop;

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
            for (var s : sessions.all()) {
                var pkt = new vq.common.Packets.S2CState();
                pkt.op   = "state";
                pkt.tick = snap.tick();

                // 1) clone map rồi mới remove chính mình (KHÔNG sửa trực tiếp snap.ents())
                var ents = new java.util.HashMap<String, vq.common.Packets.S2CState.Player>(snap.ents());
                ents.remove(s.playerId);     // bỏ “bóng”
                pkt.ents = ents;

                // 2) luôn set “you”
                var me = snap.ents().get(s.playerId);
                if (me == null) {            // phòng khi map snapshot thiếu key
                    me = new vq.common.Packets.S2CState.Player();
                    me.x = s.x; me.y = s.y;  // lấy từ Session (server authoritative)
                }
                pkt.you = me;

                s.send(pkt);
            }
            try { Thread.sleep(interval); } catch (InterruptedException ignored) {}
        }
    }
}