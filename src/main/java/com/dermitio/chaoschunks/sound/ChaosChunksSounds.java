package com.dermitio.chaoschunks.sound;

import com.dermitio.chaoschunks.ChaosChunks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


public final class ChaosChunksSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ChaosChunks.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_AMBIENT =
            SOUND_EVENTS.register("void_ambient", id -> SoundEvent.createVariableRangeEvent(id));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_GIFT =
            SOUND_EVENTS.register("void_gift", id -> SoundEvent.createVariableRangeEvent(id));

    public static final DeferredHolder<SoundEvent, SoundEvent> OVER_THE_CHUNK =
            SOUND_EVENTS.register("over_the_chunk", id -> SoundEvent.createVariableRangeEvent(id));

    public static final DeferredHolder<SoundEvent, SoundEvent> OVER_THE_CHUNK_OG =
            SOUND_EVENTS.register("over_the_chunk_og", id -> SoundEvent.createVariableRangeEvent(id));

    private ChaosChunksSounds() {}
}
