package rt.client.app;

import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.view.RenderPanel;
import rt.client.input.InputController;
import rt.client.util.Ticker;

import javax.swing.*;
import java.awt.*;

public class ClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 1) Model: giữ state local + interpolation
            WorldModel model = new WorldModel();

            // 2) Network client: WebSocket
            NetClient net = new NetClient("ws://localhost:8080/ws", model);

            // 3) View: JPanel vẽ game
            RenderPanel view = new RenderPanel(model);

            // 4) Window
            JFrame f = new JFrame("VQ Client");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setSize(960, 600);
            f.setLocationRelativeTo(null);
            f.setLayout(new BorderLayout());
            f.add(view, BorderLayout.CENTER);
            f.setVisible(true);

            // 5) Input: lắng nghe WASD và gửi press map
            InputController input = new InputController(net);
            f.addKeyListener(input.keyListener());

            // 6) Game/render loop ~60 FPS (interpolation + repaint)
            Ticker.start(60, () -> {
                model.updateInterpolation(1.0/60.0);
                view.repaint();
            });

            // 7) Kết nối WS
            net.connect();
        });
    }
}
