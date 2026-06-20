package com.dermitio.chaoschunks.server.runtime;

import com.dermitio.chaoschunks.server.command.ChaosChunksCommands;
import com.dermitio.chaoschunks.server.config.ChaosChunksServerWorldgenConfig;
import com.dermitio.chaoschunks.server.sound.ChaosChunksPlayerSoundPrefs;
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
        ChaosChunksPlayerSoundPrefs.init();
        ChaosChunksPlayerSoundPrefs.load();
        ChaosChunksServerWorldgenConfig.load();
        NeoForge.EVENT_BUS.addListener(ChaosChunksServer::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ChaosChunksCommands::register);
    }

    // ** Logs that runtime generator patching will occur later in the server startup process **
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("[ChaosChunks] ServerAboutToStart: runtime applier will patch generators on ServerStarted/LevelLoad.");
    }
}
