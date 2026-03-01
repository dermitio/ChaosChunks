package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.ChaosChunks;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;
import org.slf4j.Logger;

public final class ChaosChunksClient {

    // ** Creates a logger used for client-side diagnostic output **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Prevents instantiation since this class only provides static client hooks **
    private ChaosChunksClient() {}

    // ** Registers the ChaosChunks world preset editor with the client event system **
    public static void registerPresetEditor(RegisterPresetEditorsEvent event) {
        LOGGER.info("[ChaosChunks] RegisterPresetEditorsEvent fired");
        event.register(ChaosChunks.CHAOS_PRESET_KEY, new ChaosChunksPresetEditor());
        LOGGER.info("[ChaosChunks] Registered preset editor for {}", ChaosChunks.CHAOS_PRESET_KEY);
    }
}
