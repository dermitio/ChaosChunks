package com.dermitio.chaoschunks.client;

import com.dermitio.chaoschunks.data.ChaosChunksCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

public final class ChaosChunksPresetEditor implements PresetEditor {

    // ** Creates the preset editing screen and ensures dimensions and catalog data are initialized **
    @Override
    public Screen createEditScreen(CreateWorldScreen parent, WorldCreationContext context) {

        // ** Ensures a valid default dimension set exists before opening the preset editor **
        parent.getUiState().updateDimensions((access, dims) -> {
            if (!dims.dimensions().isEmpty()) return dims;

            var presets = access.lookupOrThrow(Registries.WORLD_PRESET);

            var normalKey = ResourceKey.create(
                    Registries.WORLD_PRESET,
                    ResourceLocation.parse("minecraft:normal")
            );

            var normalPreset = presets.getOrThrow(normalKey).value();
            return normalPreset.createWorldDimensions();
        });

        // ** Updates the catalog file from datapack resources so UI suggestions stay accurate **
        ChaosChunksCatalog.writeFromResources(Minecraft.getInstance().getResourceManager());

        // ** Opens the ChaosChunks preset configuration screen **
        return new ChaosChunksPresetScreen(parent, context);
    }
}
