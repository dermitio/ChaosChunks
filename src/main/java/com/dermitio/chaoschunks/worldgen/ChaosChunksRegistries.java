package com.dermitio.chaoschunks;

import com.dermitio.chaoschunks.worldgen.ChaosBiomeSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ChaosChunksRegistries {

    // ** Creates the deferred register used to register biome source codecs **
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCE_CODECS =
            DeferredRegister.create(Registries.BIOME_SOURCE, ChaosChunks.MODID);

    // ** Registers the Chaos biome source codec so it can be referenced by world presets **
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<? extends BiomeSource>> CHAOS_SOURCE =
            BIOME_SOURCE_CODECS.register("chaos_chunks", () -> ChaosBiomeSource.CODEC);
}
