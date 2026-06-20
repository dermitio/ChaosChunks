package com.dermitio.chaoschunks.client.time;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// =========
// Opens the custom time book screen from client-side item use //
// =========
public final class TimeBookClientHandler {

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (!event.getLevel().isClientSide()) return;
        if (!event.getItemStack().is(ChaosChunksItems.TIME_BOOK.get())) return;
        if (event.getEntity().isShiftKeyDown()) return;

        Minecraft.getInstance().setScreen(new TimeBookScreen());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private TimeBookClientHandler() {}
}
