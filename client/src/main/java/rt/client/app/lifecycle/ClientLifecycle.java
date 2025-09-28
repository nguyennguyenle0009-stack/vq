package rt.client.app.lifecycle;

import rt.client.app.config.ClientConfiguration;
import rt.client.game.ui.GameCanvas;
import rt.client.game.ui.RenderLoop;
import rt.client.game.ui.hud.HudOverlay;
import rt.client.game.ui.map.WorldMapOverlay;
import rt.client.gfx.skin.ChunkSkins;
import rt.client.model.WorldModel;
import rt.client.net.NetClient;
import rt.client.world.map.MapRenderer;
import rt.client.game.ui.render.MiniMapRenderer;
import rt.common.world.WorldGenConfig;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Coordinates the client bootstrap sequence and lifetime management. */
public final class ClientLifecycle {
    private final ClientConfiguration config;
    private final WorldModel model = new WorldModel();
    private final NetClient net;

    private ClientUiContext ui;
    private RenderLoop renderLoop;
    private ScheduledExecutorService scheduler;

    public ClientLifecycle(ClientConfiguration config) {
        this.config = config;
        this.net = new NetClient(config.serverUrl(), model);
    }

    public void start() {
        configureSystemProperties();
        initializeRenderingStatics();
        ClientUiCoordinator uiBuilder = new ClientUiCoordinator(model, net, config);
        ui = uiBuilder.buildUi();

        wireNetworking(ui.canvas(), ui.hudOverlay(), ui.worldMapOverlay());
        net.connect(config.playerName());
        startSchedulers(ui.canvas());
    }

    private void configureSystemProperties() {
        System.setProperty("VQ_LOG_DIR", config.logDirectory().toString());
        System.setProperty("playerName", config.playerName());
        if (System.getProperty("LOG_STAMP") == null) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            System.setProperty("LOG_STAMP", stamp);
        }
    }

    private void initializeRenderingStatics() {
        ChunkSkins.init();
        MapRenderer.setCache(net.chunkCache());
        MiniMapRenderer.setCache(net.chunkCache());
        MapRenderer.setPrimaryTileSizeSupplier(net::tileSize);
    }

    private void wireNetworking(GameCanvas canvas, HudOverlay hud, WorldMapOverlay overlay) {
        canvas.bindChunk(net.chunkCache(), net.tileSize());
        net.setOnTileSizeChanged(ts -> canvas.bindChunk(net.chunkCache(), ts));

        net.setOnClientPong(rttMs -> {
            canvas.setPingMs(rttMs);
            hud.setPing(rttMs);
            canvas.setPing(rttMs);
        });

        final long offlineSeed = 2000231L;
        WorldGenConfig offlineCfg = new WorldGenConfig(offlineSeed, 0.55, 0.35);
        rt.common.world.WorldGenerator.configure(offlineCfg);
        canvas.setWorldGenConfig(offlineCfg);
        overlay.setWorldGenConfig(offlineCfg);

        net.setOnSeedChanged(seed -> {
            WorldGenConfig cfg = new WorldGenConfig(seed, 0.55, 0.35);
            rt.common.world.WorldGenerator.configure(cfg);
            canvas.setWorldGenConfig(cfg);
            overlay.setWorldGenConfig(cfg);
        });
    }

    private void startSchedulers(GameCanvas canvas) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (ui.mapOpenFlag().get()) {
                net.sendInput(false, false, false, false);
            } else {
                var input = ui.inputState();
                net.sendInput(input.up, input.down, input.left, input.right);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(net::tickStreamSafe, 0, 100, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(() -> net.sendClientPing(System.nanoTime()), 2000, 2000, TimeUnit.MILLISECONDS);

        renderLoop = new RenderLoop(ui.frame(), canvas);
        renderLoop.start();

        ui.frame().addKeyListener(new rt.client.app.AdminHotkeys(net, model, canvas, ui.hudOverlay(), config.adminToken()));

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void shutdown() {
        if (renderLoop != null) {
            try { renderLoop.stop(); } catch (Exception ignored) {}
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
