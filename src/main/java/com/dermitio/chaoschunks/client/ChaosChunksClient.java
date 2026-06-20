package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.client.config.ChaosChunksConfigScreen;
import com.dermitio.chaoschunks.client.config.ChaosChunksDefaultsConfig;
import com.dermitio.chaoschunks.client.config.ChaosChunksUiEventConfig;
import com.dermitio.chaoschunks.client.hud.FreshnessHudOverlay;
import com.dermitio.chaoschunks.client.preset.ChaosChunksCatalogClient;
import com.dermitio.chaoschunks.client.preset.ChaosChunksPresetEditor;
import com.dermitio.chaoschunks.client.sound.ChaosChunksPendingSoundToggles;
import com.dermitio.chaoschunks.client.sound.ChaosChunksSoundConfig;
import com.dermitio.chaoschunks.client.time.TimeBookClientHandler;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.network.ChaosChunksSetSoundTogglePayload;
import com.dermitio.chaoschunks.network.time.TimekeepEditPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class ChaosChunksClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ChaosChunksClient() {}

    public static void initClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(ChaosChunksClient::registerPresetEditor);
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (minecraft, parent) -> new ChaosChunksConfigScreen(parent)
        );

        ChaosChunksCatalogClient.init(modBus);
        init();
        ChaosChunksExperimentsConfig.load();
        ChaosChunksDefaultsConfig.load();
        ChaosChunksSoundConfig.load();
        ChaosChunksUiEventConfig.load();
    }

    public static void registerPresetEditor(RegisterPresetEditorsEvent event) {
        LOGGER.info("[ChaosChunks] RegisterPresetEditorsEvent fired");
        event.register(ChaosChunks.CHAOS_PRESET_KEY, new ChaosChunksPresetEditor());
        LOGGER.info("[ChaosChunks] Registered preset editor for {}", ChaosChunks.CHAOS_PRESET_KEY);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FreshnessHudOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(TimeBookClientHandler::onRightClickItem);

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!canSendSoundToggle(mc)) return;

            // Full sync from saved/current client config
            for (var entry : ChaosChunksSoundConfig.snapshotEnabled().entrySet()) {
                mc.getConnection().send(
                        new ChaosChunksSetSoundTogglePayload(entry.getKey(), entry.getValue())
                );
            }

            // Then send queued overrides made while disconnected
            for (var entry : ChaosChunksPendingSoundToggles.drain().entrySet()) {
                mc.getConnection().send(
                        new ChaosChunksSetSoundTogglePayload(entry.getKey(), entry.getValue())
                );
            }
        });
    }

    public static boolean canSendSoundToggle(Minecraft mc) {
        return mc.getConnection() != null
                && mc.getConnection().hasChannel(ChaosChunksSetSoundTogglePayload.TYPE);
    }

    public static boolean canSendTimekeepEdit(Minecraft mc) {
        return mc.getConnection() != null
                && mc.getConnection().hasChannel(TimekeepEditPayload.TYPE);
    }
}
