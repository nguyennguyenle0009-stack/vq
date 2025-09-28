package rt.client.app.lifecycle;

import rt.client.app.config.ClientConfiguration;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.map.WorldMapOverlay;
import rt.client.input.InputState;
import rt.client.model.WorldModel;
import rt.client.net.NetClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates and wires the Swing UI around the networking model.
 */
public final class ClientUiCoordinator {
    private final WorldModel model;
    private final NetClient net;
    private final ClientConfiguration config;

    public ClientUiCoordinator(WorldModel model, NetClient net, ClientConfiguration config) {
        this.model = model;
        this.net = net;
        this.config = config;
    }

    public ClientUiContext buildUi() {
        GameCanvas canvas = new GameCanvas(model);
        InputState input = new InputState();
        AtomicBoolean mapOpen = new AtomicBoolean(false);

        JFrame frame = new JFrame("VQ Client - " + config.playerName());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(canvas);
        frame.setVisible(true);

        focusCanvasSoon(canvas);

        HudOverlay hud = new HudOverlay(model);
        attachHud(frame, hud);
        canvas.setHud(hud);

        WorldMapOverlay overlay = configureWorldMapOverlay(mapOpen);
        attachOverlay(frame, overlay);
        installOverlayBindings(frame, mapOpen, overlay, input, canvas);
        installMovementBindings(frame.getRootPane(), mapOpen, input);
        installFocusGuards(frame, input);

        return new ClientUiContext(canvas, hud, overlay, frame, input, mapOpen);
    }

    private WorldMapOverlay configureWorldMapOverlay(AtomicBoolean mapOpen) {
        WorldMapOverlay overlay = new WorldMapOverlay(model, net);
        overlay.setVisible(false);
        overlay.setTeleportHandler((gx, gy) -> {
            String you = model.you();
            if (you != null) {
                net.sendAdmin(config.adminToken(),
                        "teleport " + you + " " + gx + " " + gy);
            }
        });
        return overlay;
    }

    private void attachOverlay(JFrame frame, WorldMapOverlay overlay) {
        JLayeredPane layers = frame.getLayeredPane();
        layers.add(overlay, JLayeredPane.MODAL_LAYER);
        Runnable layoutOverlay = () -> {
            int ts = net.tileSize();
            if (ts <= 0) {
                ts = 32;
            }
            int w = frame.getWidth();
            int h = frame.getHeight();
            int mTop = 2 * ts;
            int mLeft = 2 * ts;
            int mRight = 2 * ts;
            int mBottom = 3 * ts;
            int ow = Math.max(200, w - mLeft - mRight);
            int oh = Math.max(150, h - mTop - mBottom);
            overlay.setBounds(mLeft, mTop, ow, oh);
        };
        layoutOverlay.run();
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutOverlay.run();
            }
        });
    }

    private void installOverlayBindings(JFrame frame,
                                        AtomicBoolean mapOpen,
                                        WorldMapOverlay overlay,
                                        InputState input,
                                        GameCanvas canvas) {
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke('M'), "toggleWorldMap");
        root.getActionMap().put("toggleWorldMap", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!overlay.isVisible()) {
                    overlay.setVisible(true);
                    overlay.openAtPlayer();
                    mapOpen.set(true);
                } else {
                    overlay.setVisible(false);
                    mapOpen.set(false);
                    clearMovementState(input);
                    net.sendInput(false, false, false, false);
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                    focusCanvasSoon(canvas);
                }
            }
        });

        installOverlayPanning(root, mapOpen, overlay);
    }

    private static void installOverlayPanning(JRootPane root,
                                              AtomicBoolean mapOpen,
                                              WorldMapOverlay overlay) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "mapPanUp");
        root.getActionMap().put("mapPanUp", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) {
                    overlay.panTiles(0, -128);
                }
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "mapPanDown");
        root.getActionMap().put("mapPanDown", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) {
                    overlay.panTiles(0, 128);
                }
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "mapPanLeft");
        root.getActionMap().put("mapPanLeft", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) {
                    overlay.panTiles(-128, 0);
                }
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "mapPanRight");
        root.getActionMap().put("mapPanRight", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (mapOpen.get()) {
                    overlay.panTiles(128, 0);
                }
            }
        });
    }

    private static void focusCanvasSoon(GameCanvas canvas) {
        SwingUtilities.invokeLater(() -> {
            canvas.setFocusable(true);
            canvas.requestFocusInWindow();
            canvas.requestFocus();
        });
    }

    private void attachHud(JFrame frame, HudOverlay hud) {
        JLayeredPane layers = frame.getLayeredPane();
        hud.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        layers.add(hud, JLayeredPane.PALETTE_LAYER);
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                hud.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            }
        });
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F4"), "toggleHud");
        frame.getRootPane().getActionMap().put("toggleHud", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                hud.setVisible(!hud.isVisible());
            }
        });
    }

    private static void clearMovementState(InputState input) {
        input.up = input.down = input.left = input.right = false;
    }

    private void installFocusGuards(JFrame frame, InputState input) {
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                clearMovementState(input);
                net.sendInput(false, false, false, false);
            }
        });
    }

    private void installMovementBindings(JRootPane root,
                                         AtomicBoolean mapOpen,
                                         InputState input) {
        bindMove(root, mapOpen, input,
                KeyStroke.getKeyStroke("pressed W"), KeyStroke.getKeyStroke("released W"),
                KeyStroke.getKeyStroke("pressed S"), KeyStroke.getKeyStroke("released S"),
                KeyStroke.getKeyStroke("pressed A"), KeyStroke.getKeyStroke("released A"),
                KeyStroke.getKeyStroke("pressed D"), KeyStroke.getKeyStroke("released D"));

        bindMove(root, mapOpen, input,
                KeyStroke.getKeyStroke("pressed UP"), KeyStroke.getKeyStroke("released UP"),
                KeyStroke.getKeyStroke("pressed DOWN"), KeyStroke.getKeyStroke("released DOWN"),
                KeyStroke.getKeyStroke("pressed LEFT"), KeyStroke.getKeyStroke("released LEFT"),
                KeyStroke.getKeyStroke("pressed RIGHT"), KeyStroke.getKeyStroke("released RIGHT"));
    }

    private static void bindMove(JRootPane root,
                                 AtomicBoolean mapOpen,
                                 InputState input,
                                 KeyStroke upPress, KeyStroke upRelease,
                                 KeyStroke downPress, KeyStroke downRelease,
                                 KeyStroke leftPress, KeyStroke leftRelease,
                                 KeyStroke rightPress, KeyStroke rightRelease) {

        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(upPress, "mv_up_p");
        am.put("mv_up_p", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.up = true;
                }
            }
        });
        im.put(upRelease, "mv_up_r");
        am.put("mv_up_r", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.up = false;
                }
            }
        });

        im.put(downPress, "mv_dn_p");
        am.put("mv_dn_p", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.down = true;
                }
            }
        });
        im.put(downRelease, "mv_dn_r");
        am.put("mv_dn_r", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.down = false;
                }
            }
        });

        im.put(leftPress, "mv_lt_p");
        am.put("mv_lt_p", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.left = true;
                }
            }
        });
        im.put(leftRelease, "mv_lt_r");
        am.put("mv_lt_r", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.left = false;
                }
            }
        });

        im.put(rightPress, "mv_rt_p");
        am.put("mv_rt_p", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.right = true;
                }
            }
        });
        im.put(rightRelease, "mv_rt_r");
        am.put("mv_rt_r", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!mapOpen.get()) {
                    input.right = false;
                }
            }
        });
    }
}
