package com.dermitio.chaoschunks.worldgen.chunk;
// time for the industrial revolution!
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import java.util.List;

// =========
// Factory for converting vanilla noise dimensions into Chaos-owned generator instances //
// =========
public final class ChaosGeneratorFactory {

    private ChaosGeneratorFactory() {}

    public static boolean canWrap(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator;
    }

    public static ChaosChunkGenerator create(
            Holder<NoiseGeneratorSettings> settings,
            long seed,
            int regionX,
            int regionZ,
            HolderSet<Biome> selectionBiomes,
            HolderSet<Biome> featureBiomes,
            long seedRandomizer
    ) {
        return new ChaosChunkGenerator(
                new ChaosBiomeSource(seed, regionX, regionZ, selectionBiomes, featureBiomes, seedRandomizer),
                settings
        );
    }

    public static ChaosChunkGenerator replaceBiomeSource(NoiseBasedChunkGenerator original, BiomeSource source) {
        return new ChaosChunkGenerator(source, original.generatorSettings());
    }
// you might have been able to tell but I kinda gave up on commenting how I feel about the code...
    public static ChaosChunkGenerator replaceBiomeSource(NoiseBasedChunkGenerator original, BiomeSource source, List<Holder<NoiseGeneratorSettings>> terrainProfiles) {
        return new ChaosChunkGenerator(source, original.generatorSettings(), terrainProfiles);
    }

    public static ChaosChunkGenerator replaceBiomeSource(
            NoiseBasedChunkGenerator original,
            BiomeSource source,
            List<Holder<NoiseGeneratorSettings>> terrainProfiles,
            List<FlatLevelGeneratorSettings> flatProfiles
    ) {
        return new ChaosChunkGenerator(source, original.generatorSettings(), terrainProfiles, flatProfiles);
    }

    public static NoiseBasedChunkGenerator restoreNoiseGenerator(NoiseBasedChunkGenerator original, BiomeSource source) {
        return new NoiseBasedChunkGenerator(source, original.generatorSettings());
    }

    public static LevelStem replaceStemBiomeSource(LevelStem original, BiomeSource source) {
        if (!(original.generator() instanceof NoiseBasedChunkGenerator noise)) return original;

        ChaosChunkGenerator generator = replaceBiomeSource(noise, source);
        try {
            generator.validate();
        } catch (Throwable ignored) {}

        return new LevelStem(original.type(), generator);
    }
}
