package com.dermitio.chaoschunks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();

    @WrapOperation(
            method = "generateFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V"
            )
    )
    private static void chaoschunks$guardApplyBiomeDecoration(
            ChunkGenerator generator,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            Operation<Void> original
    ) {
        try {
            original.call(generator, level, chunk, structureManager);
        } catch (Throwable t) {
            if (hasCause(t, IndexOutOfBoundsException.class)) {
                int n = SKIPPED.incrementAndGet();
                // log first few, then every 64th to avoid spam
                if (n == 1) {
    LOGGER.warn("[ChaosChunks] Skipping biome decoration for chunk {} due to empty/invalid feature list (#{}). Enable DEBUG for stacktrace.",
            chunk.getPos(), n);
    LOGGER.debug("[ChaosChunks] First skipped decoration stacktrace:", t);
} else if ((n & 63) == 0) {
    LOGGER.warn("[ChaosChunks] Skipping biome decoration for chunk {} due to empty/invalid feature list (#{})",
            chunk.getPos(), n);
}
                return; // treat as "no decoration" for this chunk
            }

            // anything else is still a real crash
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) return true;
            if (c.getCause() == c) break;
        }
        return false;
    }
}
