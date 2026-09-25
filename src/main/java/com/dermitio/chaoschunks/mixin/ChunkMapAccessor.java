package com.dermitio.chaoschunks.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("worldGenContext")
    WorldGenContext chaoschunks$getWorldGenContext();

    @Mutable
    @Accessor("worldGenContext")
    void chaoschunks$setWorldGenContext(WorldGenContext context);

    @Mutable
    @Accessor("chunkGeneratorState")
    void chaoschunks$setGeneratorState(ChunkGeneratorStructureState state);
}
