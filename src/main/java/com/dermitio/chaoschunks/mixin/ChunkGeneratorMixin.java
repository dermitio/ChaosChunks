package com.dermitio.chaoschunks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean LOGGED_ONCE = false;

    @WrapMethod(method = "applyBiomeDecoration")
    private void chaoschunks$guardEmptyDecorationLists(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            Operation<Void> original
    ) {
        try {
            original.call(level, chunk, structureManager);
        } catch (IndexOutOfBoundsException e) {
            // Treat as "this chunk has no valid decoration steps" -> skip decorations for this chunk.
            if (!LOGGED_ONCE) {
                LOGGED_ONCE = true;
                LOGGER.warn("[ChaosChunks] Skipped biome decoration for a chunk due to empty/short feature-step list. " +
                        "This can happen when Chaos biomes include biomes that provide 0 decoration steps.", e);
            }
        }
    }
}
