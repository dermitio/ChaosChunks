package com.dermitio.chaoschunks.sound;

import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;

public final class ChaosChunksMusics {

public static final Music OVER_THE_CHUNK = new Music(
        Holder.direct(ChaosChunksSounds.OVER_THE_CHUNK.get()),
        20 * 60 * 25,  
        20 * 60 * 32, 
        true
);

    private ChaosChunksMusics() {}
}
