package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.network.ChaosChunksSetSoundTogglePayload;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import org.slf4j.Logger;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.Minecraft;

public final class ChaosChunksClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ChaosChunksClient() {}

    public static void registerPresetEditor(RegisterPresetEditorsEvent event) {
        LOGGER.info("[ChaosChunks] RegisterPresetEditorsEvent fired");
        event.register(ChaosChunks.CHAOS_PRESET_KEY, new ChaosChunksPresetEditor());
        LOGGER.info("[ChaosChunks] Registered preset editor for {}", ChaosChunks.CHAOS_PRESET_KEY);
    }

    public static void init() {
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

    static boolean canSendSoundToggle(Minecraft mc) {
        return mc.getConnection() != null
                && mc.getConnection().hasChannel(ChaosChunksSetSoundTogglePayload.TYPE);
    }
}
