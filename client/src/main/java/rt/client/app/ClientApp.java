package rt.client.app;

import rt.client.app.config.ClientConfiguration;
import rt.client.app.lifecycle.ClientLifecycle;

import java.io.IOException;

public final class ClientApp {
    private ClientApp() {}

    public static void main(String[] args) throws IOException {
        ClientConfiguration config = ClientConfiguration.fromArgs(args);
        new ClientLifecycle(config).start();
    }
}
