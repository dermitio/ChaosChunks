package com.dermitio.chaoschunks.worldgen.biome;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

// =========
// Structure-only biome view that exposes Chaos selection biomes without changing feature sorting //
// =========
// nullscape finally wont cause issues too... hopefully
public final class ChaosStructureBiomeSource extends BiomeSource {

    private final ChaosBiomeSource delegate;

    private ChaosStructureBiomeSource(ChaosBiomeSource delegate) {
        this.delegate = delegate;
    }

    public static BiomeSource wrap(BiomeSource source) {
        return source instanceof ChaosBiomeSource chaos ? new ChaosStructureBiomeSource(chaos) : source;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return ChaosBiomeSource.CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return delegate.structureBiomes();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }
}
