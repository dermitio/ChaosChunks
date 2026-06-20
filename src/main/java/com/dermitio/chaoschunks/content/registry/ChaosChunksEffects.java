package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.content.effects.FreshnessMobEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// =========
// Registers Chaos Chunks status effects //
// =========
public final class ChaosChunksEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ChaosChunks.MODID);

    public static final DeferredHolder<MobEffect, FreshnessMobEffect> FRESHNESS =
            EFFECTS.register("freshness", FreshnessMobEffect::new);

    private ChaosChunksEffects() {}
}
