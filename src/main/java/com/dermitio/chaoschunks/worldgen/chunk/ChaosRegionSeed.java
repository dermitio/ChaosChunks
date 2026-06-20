package com.dermitio.chaoschunks.worldgen.chunk;

import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import net.minecraft.world.level.ChunkPos;

// =========
// Shared region coordinate and seed derivation used by Chaos biome and chunk generation //
// =========
public final class ChaosRegionSeed {

    private ChaosRegionSeed() {}

    public static int regionFromChunk(int chunkCoord, int regionSize) {
        return Math.floorDiv(chunkCoord, Math.max(1, regionSize));
    }

    public static int regionFromBlock(int blockCoord, int regionSize) {
        return regionFromChunk(Math.floorDiv(blockCoord, 16), regionSize);
    }

    public static long biomeSelectionSeed(long worldSeed, long seedRandomizer, int sizeX, int sizeZ, int regionX, int regionZ) {
        if (seedRandomizer != 0L) {
            return regionSeed(seedRandomizer, sizeX, sizeZ, regionX, regionZ);
        }

        long h = worldSeed;
        h ^= (long) regionX * 341873128712L;
        h ^= (long) regionZ * 132897987541L;
        return mix64(h);
    }

    public static long regionSeed(long seedRandomizer, int sizeX, int sizeZ, int regionX, int regionZ) {
        long h = seedRandomizer;
        h ^= (long) sizeX * 0x9E3779B97F4A7C15L;
        h ^= (long) sizeZ * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) regionX * 0xD6E8FEB86659FD93L;
        h ^= (long) regionZ * 0xA5A3564E27F1A9D5L;
        return mix64(h);
    }

    public static long terrainProfileSeed(long terrainRandomizer, int sizeX, int sizeZ, int regionX, int regionZ) {
        long h = terrainRandomizer;
        h ^= (long) sizeX * 0x94D049BB133111EBL;
        h ^= (long) sizeZ * 0x369DEA0F31A53F85L;
        h ^= (long) regionX * 0xDB4F0B9175AE2165L;
        h ^= (long) regionZ * 0xBBE0563303A4615FL;
        return mix64(h);
    }
// I honestly have 0 clue what the hell I am doing anymore
    public static long dimensionSeed(long worldSeed, long seedRandomizer, int sizeX, int sizeZ) {
        if (seedRandomizer == 0L) return worldSeed;

        long h = seedRandomizer;
        h ^= (long) sizeX * 0x632BE59BD9B4E019L;
        h ^= (long) sizeZ * 0x85157AF5L;
        return mix64(h);
    }

    public static long seedForChunk(ChaosBiomeSource source, long fallbackSeed, ChunkPos pos) {
        if (!source.usesIndependentRegionSeed()) return fallbackSeed;

        int regionX = regionFromChunk(pos.x(), source.regionSizeX());
        int regionZ = regionFromChunk(pos.z(), source.regionSizeZ());
        return regionSeed(source.seedRandomizer(), source.regionSizeX(), source.regionSizeZ(), regionX, regionZ);
    }

    public static long cacheKey(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    public static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
