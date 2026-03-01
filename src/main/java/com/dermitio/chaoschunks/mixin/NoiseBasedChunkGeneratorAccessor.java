package com.dermitio.chaoschunks.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {

    // ** Exposes the biomeSource field from ChunkGenerator for reading **
    @Accessor("biomeSource")
    BiomeSource chaoschunks$getBiomeSource();

    // ** Allows replacing the biomeSource field inside ChunkGenerator **
    @Mutable
    @Accessor("biomeSource")
    void chaoschunks$setBiomeSource(BiomeSource source);
}
