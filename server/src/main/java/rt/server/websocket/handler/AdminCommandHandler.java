package rt.server.websocket.handler;

import rt.common.net.dto.AdminResultS2C;
import rt.server.session.SessionRegistry;
import rt.server.config.ServerConfig;
import rt.server.world.World;
import rt.server.world.geo.ContinentIndex;

import java.util.StringJoiner;

/** Handles admin commands routed from the websocket handler. */
public final class AdminCommandHandler {
    private final SessionRegistry sessions;
    private final World world;
    private final ServerConfig config;
    private final ContinentIndex continents;

    public AdminCommandHandler(SessionRegistry sessions, World world, ContinentIndex continents, ServerConfig config) {
        this.sessions = sessions;
        this.world = world;
        this.continents = continents;
        this.config = config;
    }

    public AdminResultS2C handle(SessionRegistry.Session session, String rawCommand) {
        String cmd = rawCommand == null ? "" : rawCommand.trim();
        try {
            if (cmd.equals("listSessions")) {
                StringJoiner sj = new StringJoiner(" ");
                sessions.all().forEach(s -> sj.add(s.playerId));
                return new AdminResultS2C(true, "sessions: " + sj.toString());
            }
            if (cmd.startsWith("teleport ")) {
                String[] parts = cmd.split("\\s+");
                if (parts.length != 4) {
                    return new AdminResultS2C(false, "usage: teleport <id> <x> <y>");
                }
                boolean ok = world.teleport(parts[1], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                return new AdminResultS2C(ok, ok ? "teleported" : "failed (blocked/out-of-bounds)");
            }
            if (cmd.equals("reloadMap")) {
                boolean ok = world.reloadMap(config.mapResourcePath);
                return new AdminResultS2C(ok, ok ? "map reloaded" : "reload failed");
            }
            if (cmd.equals("cont here")) {
                long tx = Math.round(session.x);
                long ty = Math.round(session.y);
                int cid = continents.idAtTile(tx, ty);
                var meta = continents.meta(cid);
                String msg = "contId=" + cid + (meta != null ? " name=" + meta.name + " areaCells=" + meta.areaCells : "");
                return new AdminResultS2C(true, msg);
            }
            if (cmd.equals("cont list")) {
                StringBuilder sb = new StringBuilder();
                for (var meta : continents.all()) {
                    sb.append(meta.id).append(' ').append(meta.name)
                            .append(" area=").append(meta.areaCells).append('\n');
                }
                return new AdminResultS2C(true, sb.length() == 0 ? "(empty)" : sb.toString());
            }
            if (cmd.startsWith("cont goto ")) {
                int cid = Integer.parseInt(cmd.substring(9).trim());
                var meta = continents.meta(cid);
                if (meta == null) {
                    return new AdminResultS2C(false, "unknown continent id");
                }
                int cell = continents.cellSizeTiles();
                double tx = meta.ax * (double) cell + cell * 0.5;
                double ty = meta.ay * (double) cell + cell * 0.5;
                boolean ok = world.teleport(session.playerId, tx, ty);
                return new AdminResultS2C(ok,
                        ok ? "teleported to " + meta.name + " (#" + cid + ")" : "teleport failed");
            }
            return new AdminResultS2C(false, "unknown cmd");
        } catch (Exception ex) {
            return new AdminResultS2C(false, "error: " + ex.getMessage());
        }
    }
}
