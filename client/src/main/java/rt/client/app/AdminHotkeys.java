package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/** Xử lý phím chức năng (F1..F4) và chuyển đổi HUD dev. */
public class AdminHotkeys extends KeyAdapter {
    private final NetClient net;
    private final WorldModel model;
    private final GameCanvas panel;
    private final HudOverlay hud;
    private final String adminToken;
    private boolean devHudVisible = false;

    public AdminHotkeys(NetClient net, WorldModel model, GameCanvas panel, HudOverlay hud, String adminToken) {
        this.net = net;
        this.model = model;
        this.panel = panel;
        this.hud = hud;
        this.adminToken = adminToken;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_F1 -> net.sendAdmin(adminToken, "listSessions");
            case KeyEvent.VK_F2 -> {
                String you = model.you();
                if (you != null) net.sendAdmin(adminToken, "teleport " + you + " 5 5");
            }
            case KeyEvent.VK_F3 -> net.sendAdmin(adminToken, "reloadMap");
            case KeyEvent.VK_F4 -> {
                devHudVisible = !devHudVisible;
                panel.setDevHud(devHudVisible);
                if (hud != null) hud.setVisible(devHudVisible);
            }
            case KeyEvent.VK_M -> panel.toggleMinimapScale();
            case KeyEvent.VK_N -> panel.toggleMinimapOrientation();
            default -> {}
        }
    }
}
