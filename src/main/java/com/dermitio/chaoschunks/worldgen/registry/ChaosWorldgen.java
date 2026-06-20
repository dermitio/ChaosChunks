package com.dermitio.chaoschunks.worldgen.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ChaosWorldgen {

    // ** Creates a deferred register for custom biome source codecs **
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, ChaosChunks.MODID);

    // ** Creates a deferred register for custom chunk generator codecs **
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ChaosChunks.MODID);

    // ** Registers the Chaos biome source codec so the engine can deserialize the custom worldgen **
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<? extends BiomeSource>> CHAOS =
            BIOME_SOURCES.register(
                    "chaos_chunks",
                    () -> ChaosBiomeSource.CODEC
            );

    // ** Registers the Chaos noise generator so world presets can own the whole generation path **
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<? extends ChunkGenerator>> CHAOS_NOISE =
            CHUNK_GENERATORS.register(
                    "noise",
                    () -> ChaosChunkGenerator.CODEC
            );
}
// do I even care of this?
