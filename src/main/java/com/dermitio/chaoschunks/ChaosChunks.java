package com.dermitio.chaoschunks;

import com.dermitio.chaoschunks.client.ChaosChunksClient;
import com.dermitio.chaoschunks.worldgen.ChaosWorldgen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import com.dermitio.chaoschunks.server.ChaosChunksServer;
import com.dermitio.chaoschunks.server.ChaosChunksNotices;
import com.dermitio.chaoschunks.server.ChaosChunksNoticeRules;
import com.dermitio.chaoschunks.sound.ChaosChunksSoundRules;
import com.dermitio.chaoschunks.sound.ChaosChunksSoundTicker;
import com.dermitio.chaoschunks.sound.ChaosChunksSounds;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.dermitio.chaoschunks.client.ChaosChunksConfigScreen;
import com.dermitio.chaoschunks.client.ChaosChunksSoundConfig;
import com.dermitio.chaoschunks.network.ChaosChunksNetwork;
@Mod(ChaosChunks.MODID)
public class ChaosChunks {

    // ** Defines the mod ID used across registries and resource locations **
    public static final String MODID = "chaoschunks";

    // ** Creates and stores the resource key for the Chaos world preset **
    public static final ResourceKey<WorldPreset> CHAOS_PRESET_KEY =
            ResourceKey.create(
                    Registries.WORLD_PRESET,
                    Identifier.parse(MODID + ":chaos_chunks")
            );

    // ** Initializes the mod, registers worldgen, client UI hooks, runtime events, and server logic **
    public ChaosChunks(IEventBus modBus, ModContainer container) {
    ChaosWorldgen.BIOME_SOURCES.register(modBus);
    ChaosChunksSounds.SOUND_EVENTS.register(modBus);
    modBus.addListener(ChaosChunksClient::registerPresetEditor);

    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            com.dermitio.chaoschunks.server.ChaosChunksRuntimeApplier::onServerStarted
    );
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            com.dermitio.chaoschunks.server.ChaosChunksRuntimeApplier::onLevelLoad
    );

    container.registerExtensionPoint(
            IConfigScreenFactory.class,
            (minecraft, parent) -> new ChaosChunksConfigScreen(parent)
    );

    com.dermitio.chaoschunks.client.ChaosChunksCatalogClient.init(modBus);
    ChaosChunksClient.init();
    ChaosChunksSoundConfig.load();
    ChaosChunksNoticeRules.registerAll();
    ChaosChunksNetwork.init(modBus);
    ChaosChunksNotices.init();
    ChaosChunksSoundRules.registerAll();
    ChaosChunksSoundTicker.init();
    ChaosChunksServer.init();

}
}
