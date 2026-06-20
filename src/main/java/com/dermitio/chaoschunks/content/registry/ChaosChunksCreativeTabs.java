package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// =========
// Registers the dedicated ChaosChunks creative inventory category //
// =========
public final class ChaosChunksCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChaosChunks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CHAOS_CHUNKS = TABS.register(
            "chaos_chunks",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .title(Component.translatable("itemGroup.chaoschunks.chaos_chunks"))
                    .icon(() -> new ItemStack(ChaosChunksExperimentsConfig.timeVoidMint()
                            ? ChaosChunksItems.MINT.get()
                            : Items.BARRIER))
                    .displayItems((parameters, output) -> {
                        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

                        output.accept(ChaosChunksItems.TIME_BOOK.get());
                        output.accept(ChaosChunksItems.VOID_ESSENCE.get());
                        output.accept(ChaosChunksItems.MINT.get());
                        output.accept(ChaosChunksItems.DEEPBREATH.get());
                        output.accept(ChaosChunksItems.DEEPBREATH_STRONG.get());
                        output.accept(ChaosChunksItems.DEEPBREATH_LONG.get());
                        output.accept(ChaosChunksItems.MINT_BUSH.get());
                    })
                    .build()
    );

    private ChaosChunksCreativeTabs() {}
}
