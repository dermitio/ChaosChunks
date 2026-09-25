package com.dermitio.chaoschunks.mixin;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.client.config.ChaosChunksDefaultsConfig;
import com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {

    @Shadow
    public abstract WorldCreationUiState.WorldTypeEntry getWorldType();

    @Inject(method = "setWorldType", at = @At("HEAD"))
    private void chaoschunks$onSetWorldType(WorldCreationUiState.WorldTypeEntry entry, CallbackInfo ci) {
        ResourceKey<WorldPreset> presetKey = entry.preset() == null ? null : entry.preset().unwrapKey().orElse(null);

        if (ChaosChunks.CHAOS_PRESET_KEY.equals(presetKey)) {
            // Datapack/preset refreshes can reselect the same world type. Keep the user's choices.
            var previous = getWorldType();
            if (previous != null && previous.preset() != null
                    && previous.preset().is(ChaosChunks.CHAOS_PRESET_KEY)
                    && ChaosChunksPendingConfig.peek() != null) return;
            ChaosChunksPendingConfig.set(1, 1, "", Map.of(), defaultDimensionModes(), defaultSeedRandomizers(), defaultTerrainRandomizers());
        } else {
            ChaosChunksPendingConfig.clear();
        }
    }

    private static Map<String, String> defaultDimensionModes() {
        return Map.of(ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID, ChaosChunksDefaultsConfig.defaultDimensionMode().name());
    }

    private static Map<String, Long> defaultSeedRandomizers() {
        if (ChaosChunksDefaultsConfig.regionSeedMode() != ChaosChunksDefaultsConfig.RegionSeedMode.RANDOMIZED_REGION_SEED) {
            return Map.of();
        }

        return Map.of(
                ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID,
                ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS
        );
    }

    private static Map<String, Long> defaultTerrainRandomizers() {
        if (!ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()) {
            return Map.of();
        }

        if (ChaosChunksDefaultsConfig.terrainProfileMode() != ChaosChunksDefaultsConfig.TerrainProfileMode.RANDOMIZED_TERRAIN_PROFILES) {
            return Map.of();
        }

        return Map.of(
                ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID,
                ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS
        );
    }

}
