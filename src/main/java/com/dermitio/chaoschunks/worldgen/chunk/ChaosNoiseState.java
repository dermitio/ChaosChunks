package com.dermitio.chaoschunks.worldgen.chunk;

import com.dermitio.chaoschunks.mixin.RandomStateAccessor;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.LinkedHashMap;
import java.util.Map;

// =========
// Builds and caches region-local RandomStates for terrain, surface, and carver generation //
// =========
final class ChaosNoiseState {

    // Keep this bounded; RandomState owns noise objects and can become expensive when region size is small.
    private static final int MAX_REGION_RANDOM_STATES = 192;
    private static final long GLOBAL_STATE_REGION_KEY = Long.MIN_VALUE;

    private final Holder<NoiseGeneratorSettings> settings;
    private final Map<Long, RandomState> regionStates = new LinkedHashMap<>(MAX_REGION_RANDOM_STATES, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, RandomState> eldest) {
            return size() > MAX_REGION_RANDOM_STATES;
        }
    };

    ChaosNoiseState(Holder<NoiseGeneratorSettings> settings) {
        this.settings = settings;
    }

    RandomState forChunk(BiomeSource source, RandomState fallback, ChunkPos pos, Holder<NoiseGeneratorSettings> settings, int profileIndex) {
        if (!(source instanceof ChaosBiomeSource chaos) || usesFallbackState(chaos, profileIndex)) {
            return fallback;
        }

        int regionX = ChaosRegionSeed.regionFromChunk(pos.x(), chaos.regionSizeX());
        int regionZ = ChaosRegionSeed.regionFromChunk(pos.z(), chaos.regionSizeZ());
        return forRegion(chaos, fallback, regionX, regionZ, settings, profileIndex);
    }

    RandomState forBlock(BiomeSource source, RandomState fallback, int blockX, int blockZ, Holder<NoiseGeneratorSettings> settings, int profileIndex) {
        if (!(source instanceof ChaosBiomeSource chaos) || usesFallbackState(chaos, profileIndex)) {
            return fallback;
        }

        int regionX = ChaosRegionSeed.regionFromBlock(blockX, chaos.regionSizeX());
        int regionZ = ChaosRegionSeed.regionFromBlock(blockZ, chaos.regionSizeZ());
        return forRegion(chaos, fallback, regionX, regionZ, settings, profileIndex);
    }

    private RandomState forRegion(ChaosBiomeSource chaos, RandomState fallback, int regionX, int regionZ, Holder<NoiseGeneratorSettings> settings, int profileIndex) {
        if (!((Object) fallback instanceof RandomStateAccessor accessor)) {
            return fallback;
        }

        boolean independentSeed = chaos.usesIndependentRegionSeed();
        long regionKey = independentSeed ? ChaosRegionSeed.cacheKey(regionX, regionZ) : GLOBAL_STATE_REGION_KEY;
        long cacheKey = regionKey ^ (((long) profileIndex) * 0x9E3779B97F4A7C15L);
        synchronized (regionStates) {
            RandomState cached = regionStates.get(cacheKey);
            if (cached != null) return cached;

            long seed = independentSeed
                    ? ChaosRegionSeed.regionSeed(
                            chaos.seedRandomizer(),
                            chaos.regionSizeX(),
                            chaos.regionSizeZ(),
                            regionX,
                            regionZ
                    )
                    : chaos.seed();
            Holder<NoiseGeneratorSettings> effectiveSettings = settings == null ? this.settings : settings;
            RandomState created = RandomState.create(effectiveSettings.value(), accessor.chaoschunks$getNoises(), seed);
            regionStates.put(cacheKey, created);
            return created;
        }
    }

    private static boolean usesFallbackState(ChaosBiomeSource chaos, int profileIndex) {
        return profileIndex == 0 && !chaos.usesIndependentRegionSeed();
    }
}
