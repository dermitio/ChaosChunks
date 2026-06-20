package com.dermitio.chaoschunks.client.sound;

import com.dermitio.chaoschunks.ChaosChunks;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

@EventBusSubscriber(modid = ChaosChunks.MODID, value = Dist.CLIENT)
public final class ChaosChunksMusicHooks {

    private ChaosChunksMusicHooks() {}

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (ChaosChunksMusicMixer.shouldUseMixer(mc)) {
            event.overrideMusic(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ChaosChunksMusicMixer.tick();
    }
}
