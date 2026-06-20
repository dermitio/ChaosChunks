package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

public final class ChaosChunksBrewing {

    private ChaosChunksBrewing() {}

    public static void register(RegisterBrewingRecipesEvent event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        event.getBuilder().addRecipe(
                Ingredient.of(ChaosChunksItems.VOID_ESSENCE.get()),
                Ingredient.of(ChaosChunksItems.MINT.get()),
                new ItemStack(ChaosChunksItems.DEEPBREATH.get())
        );
        event.getBuilder().addRecipe(
                Ingredient.of(ChaosChunksItems.DEEPBREATH.get()),
                Ingredient.of(ChaosChunksItems.VOID_ESSENCE.get()),
                new ItemStack(ChaosChunksItems.DEEPBREATH_STRONG.get())
        );
        event.getBuilder().addRecipe(
                Ingredient.of(ChaosChunksItems.DEEPBREATH.get()),
                Ingredient.of(ChaosChunksItems.MINT.get()),
                new ItemStack(ChaosChunksItems.DEEPBREATH_LONG.get())
        );
    }
}
