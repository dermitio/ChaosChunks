package com.dermitio.chaoschunks.content.voids;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// =========
// Handles capturing void essence from the air in the void biome //
// =========
public final class VoidEssenceCapture {

    private static final int MIN_CAPTURE_Y = 64;

    // =========
    // Converts an empty bottle into void essence when used in valid void air //
    // =========
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (!stack.is(Items.GLASS_BOTTLE)) return;
        if (player.getY() <= MIN_CAPTURE_Y) return;
        if (!event.getLevel().getBiome(player.blockPosition()).is(Biomes.THE_VOID)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack essence = new ItemStack(ChaosChunksItems.VOID_ESSENCE.get());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        if (stack.isEmpty()) {
            player.setItemInHand(event.getHand(), essence);
        } else if (!player.getInventory().add(essence)) {
            player.drop(essence, false);
        }

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.BOTTLE_FILL,
                SoundSource.PLAYERS,
                1.0F,
                0.8F + level.getRandom().nextFloat() * 0.4F
        );
    }

    private VoidEssenceCapture() {}
}
