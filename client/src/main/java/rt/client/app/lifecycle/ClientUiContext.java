package rt.client.app.lifecycle;

import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.map.WorldMapOverlay;
import rt.client.input.InputState;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bundles Swing widgets created for the client window. */
public record ClientUiContext(GameCanvas canvas,
                              HudOverlay hudOverlay,
                              WorldMapOverlay worldMapOverlay,
                              JFrame frame,
                              InputState inputState,
                              AtomicBoolean mapOpenFlag) {
}
