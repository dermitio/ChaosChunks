package com.dermitio.chaoschunks;

import com.dermitio.chaoschunks.content.registry.ChaosChunksBlocks;
import com.dermitio.chaoschunks.content.registry.ChaosChunksBrewing;
import com.dermitio.chaoschunks.content.registry.ChaosChunksCreativeTabs;
import com.dermitio.chaoschunks.content.registry.ChaosChunksEffects;
import com.dermitio.chaoschunks.content.effects.FreshnessMobEffect;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import com.dermitio.chaoschunks.content.voids.VoidEssenceCapture;
import com.dermitio.chaoschunks.content.voids.VoidEssenceFishing;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.network.ChaosChunksNetwork;
import com.dermitio.chaoschunks.server.runtime.ChaosChunksServer;
import com.dermitio.chaoschunks.server.notice.ChaosChunksNotices;
import com.dermitio.chaoschunks.server.notice.ChaosChunksNoticeRules;
import com.dermitio.chaoschunks.server.time.TimekeepSync;
import com.dermitio.chaoschunks.server.time.TimekeepUnlocks;
import com.dermitio.chaoschunks.server.voids.VoidGiftEvent;
import com.dermitio.chaoschunks.sound.ChaosChunksSoundRules;
import com.dermitio.chaoschunks.sound.ChaosChunksSoundTicker;
import com.dermitio.chaoschunks.sound.ChaosChunksSounds;
import com.dermitio.chaoschunks.worldgen.registry.ChaosWorldgen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;

@Mod(ChaosChunks.MODID)
// =========
// Main mod bootstrap for registries, UI hooks, runtime worldgen, networking, and sound systems //
// =========
public class ChaosChunks {

    // ** Defines the mod ID used across registries and resource locations **
    public static final String MODID = "chaoschunks";

    // ** Creates and stores the resource key for the Chaos world preset **
    public static final ResourceKey<WorldPreset> CHAOS_PRESET_KEY =
            ResourceKey.create(
                    Registries.WORLD_PRESET,
                    Identifier.parse(MODID + ":chaos_chunks")
            );

    // =========
    // Registers all mod entry points with NeoForge during construction //
    // =========
    public ChaosChunks(IEventBus modBus, ModContainer container) {
        ChaosChunksExperimentsConfig.load();

        ChaosChunksBlocks.BLOCKS.register(modBus);
        ChaosChunksEffects.EFFECTS.register(modBus);
        ChaosChunksItems.ITEMS.register(modBus);
        ChaosChunksCreativeTabs.TABS.register(modBus);
        ChaosWorldgen.BIOME_SOURCES.register(modBus);
        ChaosWorldgen.CHUNK_GENERATORS.register(modBus);
        ChaosChunksSounds.SOUND_EVENTS.register(modBus);
        modBus.addListener(ChaosChunksItems::addToCreativeTabs);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.dermitio.chaoschunks.server.runtime.ChaosChunksRuntimeApplier::onServerStarted
        );
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.dermitio.chaoschunks.server.runtime.ChaosChunksRuntimeApplier::onLevelLoad
        );
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.dermitio.chaoschunks.server.runtime.ChaosChunksRuntimeApplier::onServerTick
        );
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FreshnessMobEffect::onLivingDamage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VoidEssenceCapture::onRightClickItem);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VoidEssenceFishing::onItemFished);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ChaosChunksBrewing::register);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            initClient(modBus, container);
        }

        ChaosChunksNoticeRules.registerAll();
        ChaosChunksNetwork.init(modBus);
        ChaosChunksNotices.init();
        ChaosChunksSoundRules.registerAll();
        ChaosChunksSoundTicker.init();
        ChaosChunksServer.init();
        TimekeepSync.init();
        TimekeepUnlocks.init();
        VoidGiftEvent.init();
    }

    private static void initClient(IEventBus modBus, ModContainer container) {
        try {
            Class<?> client = Class.forName("com.dermitio.chaoschunks.client.ChaosChunksClient");
            client.getMethod("initClient", IEventBus.class, ModContainer.class).invoke(null, modBus, container);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize ChaosChunks client hooks", e);
        }
    }
}
