package com.dermitio.chaoschunks.worldgen;

import com.dermitio.chaoschunks.ChaosChunks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ChaosWorldgen {

    // ** Creates a deferred register for custom biome source codecs **
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, ChaosChunks.MODID);

    // ** Registers the Chaos biome source codec so the engine can deserialize the custom worldgen **
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<? extends BiomeSource>> CHAOS =
            BIOME_SOURCES.register(
                    "chaos_chunks",
                    () -> ChaosBiomeSource.CODEC
            );
}
