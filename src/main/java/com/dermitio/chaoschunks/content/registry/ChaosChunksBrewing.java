package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registers the experiment condition used by the data-driven brewing recipes. */
public final class ChaosChunksBrewing {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.CONDITION_SERIALIZERS, ChaosChunks.MODID);

    static {
        CONDITIONS.register("time_void_mint_enabled", () -> TimeVoidMintEnabled.CODEC);
    }

    private ChaosChunksBrewing() {}

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }

    public record TimeVoidMintEnabled() implements ICondition {
        public static final MapCodec<TimeVoidMintEnabled> CODEC = MapCodec.unit(new TimeVoidMintEnabled());

        @Override
        public boolean test(IContext context) {
            return ChaosChunksExperimentsConfig.timeVoidMint();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
