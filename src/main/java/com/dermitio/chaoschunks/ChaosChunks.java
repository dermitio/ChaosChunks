package com.dermitio.chaoschunks;

import com.dermitio.chaoschunks.client.ChaosChunksClient;
import com.dermitio.chaoschunks.worldgen.ChaosWorldgen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import com.dermitio.chaoschunks.server.ChaosChunksServer;
import com.dermitio.chaoschunks.server.ChaosChunksNotices;
import com.dermitio.chaoschunks.server.ChaosChunksNoticeRules;

@Mod(ChaosChunks.MODID)
public class ChaosChunks {

    // ** Defines the mod ID used across registries and resource locations **
    public static final String MODID = "chaoschunks";

    // ** Creates and stores the resource key for the Chaos world preset **
    public static final ResourceKey<WorldPreset> CHAOS_PRESET_KEY =
            ResourceKey.create(
                    Registries.WORLD_PRESET,
                    ResourceLocation.parse(MODID + ":chaos_chunks")
            );

    // ** Initializes the mod, registers worldgen, client UI hooks, runtime events, and server logic **
    public ChaosChunks(IEventBus modBus) {
        ChaosWorldgen.BIOME_SOURCES.register(modBus);
        modBus.addListener(ChaosChunksClient::registerPresetEditor);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.dermitio.chaoschunks.server.ChaosChunksRuntimeApplier::onServerStarted
        );
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.dermitio.chaoschunks.server.ChaosChunksRuntimeApplier::onLevelLoad
        );

        com.dermitio.chaoschunks.client.ChaosChunksCatalogClient.init(modBus);
        ChaosChunksNoticeRules.registerAll();
        ChaosChunksNotices.init();
        ChaosChunksServer.init();
    }
}
