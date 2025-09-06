package rt.server.main;

import rt.server.websocket.WsServer;

public class MainServer {
    public static void main(String[] args) throws Exception {
        // Khởi động server WebSocket lắng nghe cổng 8080
        WsServer server = new WsServer(8080);
        server.start();
        System.out.println("Server started at ws://localhost:8080/ws");
    }
}
