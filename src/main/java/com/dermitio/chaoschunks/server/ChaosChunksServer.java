package com.dermitio.chaoschunks.server;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

public final class ChaosChunksServer {

    // ** Creates a logger used for server lifecycle diagnostics **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Prevents instantiation since this class only provides static server hooks **
    private ChaosChunksServer() {}

    // ** Registers server lifecycle listeners required for runtime worldgen patching **
    public static void init() {
        NeoForge.EVENT_BUS.addListener(ChaosChunksServer::onServerAboutToStart);
    }

    // ** Logs that runtime generator patching will occur later in the server startup process **
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("[ChaosChunks] ServerAboutToStart: runtime applier will patch generators on ServerStarted/LevelLoad.");
    }
}
