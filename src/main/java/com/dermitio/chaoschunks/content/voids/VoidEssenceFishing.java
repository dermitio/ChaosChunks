package com.dermitio.chaoschunks.content.voids;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

// =========
// Adds a very rare void essence catch while fishing in the void biome //
// =========
public final class VoidEssenceFishing {

    private static final int VOID_ESSENCE_CHANCE_DENOMINATOR = 10_000;

    public static void onItemFished(ItemFishedEvent event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (!(event.getHookEntity().level() instanceof ServerLevel level)) return;
        if (!level.getBiome(event.getHookEntity().blockPosition()).is(Biomes.THE_VOID)) return;
        if (level.getRandom().nextInt(VOID_ESSENCE_CHANCE_DENOMINATOR) != 0) return;

        event.getDrops().add(new ItemStack(ChaosChunksItems.VOID_ESSENCE.get()));
    }

    private VoidEssenceFishing() {}
}
