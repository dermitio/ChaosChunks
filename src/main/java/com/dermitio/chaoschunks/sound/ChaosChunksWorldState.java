package com.dermitio.chaoschunks.sound;

import com.dermitio.chaoschunks.mixin.NoiseBasedChunkGeneratorAccessor;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import net.minecraft.server.MinecraftServer;

public final class ChaosChunksWorldState {

    private ChaosChunksWorldState() {}

    public static boolean isChaosWorld(MinecraftServer server) {
        try {
            if (server == null || server.overworld() == null) return false;

            var level = server.overworld();
            var gen = level.getChunkSource().getGenerator();

            if (!((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc)) return false;
            return acc.chaoschunks$getBiomeSource() instanceof ChaosBiomeSource;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
