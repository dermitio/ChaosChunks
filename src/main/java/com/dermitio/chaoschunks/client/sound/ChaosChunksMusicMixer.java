package com.dermitio.chaoschunks.client.sound;

import com.dermitio.chaoschunks.sound.ChaosChunksSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;

public final class ChaosChunksMusicMixer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int CHECK_INTERVAL_TICKS = 35;
    private static final int FADE_TICKS = 20 * 4;
    private static final String REMASTER_KEY = "over_the_chunk";
    private static final String ORIGINAL_KEY = "over_the_chunk_og";

    private static FadingMusicSound remaster;
    private static FadingMusicSound original;
    private static long lastCheckGameTime = Long.MIN_VALUE;
    private static boolean remasterInFront = true;
    private static Boolean separatedRemasterSelected;

    private ChaosChunksMusicMixer() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!shouldUseMixer(mc)) {
            stop();
            return;
        }

        long gameTime = level.getGameTime();
        if (lastCheckGameTime != Long.MIN_VALUE && gameTime - lastCheckGameTime < CHECK_INTERVAL_TICKS) {
            return;
        }

        lastCheckGameTime = gameTime;

        boolean remasterEnabled = ChaosChunksSoundConfig.isEnabled(REMASTER_KEY);
        boolean originalEnabled = ChaosChunksSoundConfig.isEnabled(ORIGINAL_KEY);
        if (!remasterEnabled && !originalEnabled) {
            stop();
            return;
        }

        if (ChaosChunksSoundConfig.overTheChunkMode() == ChaosChunksSoundConfig.OverTheChunkMode.SEPARATED) {
            tickSeparated(mc, remasterEnabled, originalEnabled);
            return;
        }

        separatedRemasterSelected = null;
        tickMixed(mc, remasterEnabled, originalEnabled);
    }

    private static void tickMixed(Minecraft mc, boolean remasterEnabled, boolean originalEnabled) {
        if (remasterEnabled) {
            remaster = ensurePlaying(mc, remaster, ChaosChunksSounds.OVER_THE_CHUNK.get(), remasterInFront ? 1.0F : 0.0F);
        } else {
            stop(remaster);
            remaster = null;
        }

        if (originalEnabled) {
            original = ensurePlaying(mc, original, ChaosChunksSounds.OVER_THE_CHUNK_OG.get(), remasterInFront ? 0.0F : 1.0F);
        } else {
            stop(original);
            original = null;
        }

        if (remasterEnabled && originalEnabled) {
            if (!remaster.isFading() && !original.isFading()) {
                String previousFront = remasterInFront ? REMASTER_KEY : ORIGINAL_KEY;
                remasterInFront = !remasterInFront;
                String nextFront = remasterInFront ? REMASTER_KEY : ORIGINAL_KEY;
                LOGGER.debug("[ChaosChunks] Music mixer switch: {} -> {} over {} ticks", previousFront, nextFront, FADE_TICKS);
                remaster.fadeTo(remasterInFront ? 1.0F : 0.0F, FADE_TICKS);
                original.fadeTo(remasterInFront ? 0.0F : 1.0F, FADE_TICKS);
            }
        } else if (remaster != null) {
            remaster.fadeTo(1.0F, FADE_TICKS);
        } else if (original != null) {
            original.fadeTo(1.0F, FADE_TICKS);
        }
    }

    private static void tickSeparated(Minecraft mc, boolean remasterEnabled, boolean originalEnabled) {
        boolean useRemaster = chooseSeparatedTrack(remasterEnabled, originalEnabled);
        if (useRemaster) {
            remaster = ensurePlaying(mc, remaster, ChaosChunksSounds.OVER_THE_CHUNK.get(), 1.0F);
            remaster.fadeTo(1.0F, 1);
            stop(original);
            original = null;
        } else {
            original = ensurePlaying(mc, original, ChaosChunksSounds.OVER_THE_CHUNK_OG.get(), 1.0F);
            original.fadeTo(1.0F, 1);
            stop(remaster);
            remaster = null;
        }
    }

    private static boolean chooseSeparatedTrack(boolean remasterEnabled, boolean originalEnabled) {
        if (remasterEnabled && !originalEnabled) {
            separatedRemasterSelected = true;
        } else if (!remasterEnabled && originalEnabled) {
            separatedRemasterSelected = false;
        } else if (separatedRemasterSelected == null) {
            separatedRemasterSelected = RANDOM.nextBoolean();
            LOGGER.debug(
                    "[ChaosChunks] Music separated selection: {}",
                    separatedRemasterSelected ? REMASTER_KEY : ORIGINAL_KEY
            );
        }

        return Boolean.TRUE.equals(separatedRemasterSelected);
    }

    public static boolean shouldUseMixer(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return false;
        }

        return shouldUseChaosMusic(mc.level, mc.player.blockPosition())
                && (ChaosChunksSoundConfig.isEnabled(REMASTER_KEY) || ChaosChunksSoundConfig.isEnabled(ORIGINAL_KEY));
    }

    public static void resetPlayback() {
        stop();
    }

    public static void stop() {
        stop(remaster);
        stop(original);
        remaster = null;
        original = null;
        lastCheckGameTime = Long.MIN_VALUE;
        remasterInFront = true;
        separatedRemasterSelected = null;
    }

    private static FadingMusicSound ensurePlaying(Minecraft mc, FadingMusicSound sound, SoundEvent event, float initialVolume) {
        if (sound == null || sound.isStopped() || !mc.getSoundManager().isActive(sound)) {
            sound = new FadingMusicSound(event, initialVolume);
            mc.getSoundManager().play(sound);
        }

        return sound;
    }

    private static void stop(FadingMusicSound sound) {
        if (sound != null && !sound.isStopped()) {
            sound.stopSound();
        }
    }

    private static boolean shouldUseChaosMusic(ClientLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(BiomeTags.IS_END) || biome.is(Biomes.THE_VOID);
    }

    private static final class FadingMusicSound extends AbstractTickableSoundInstance {
        private float currentVolume;
        private float targetVolume;
        private int fadeTicksRemaining;

        private FadingMusicSound(SoundEvent event, float initialVolume) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.currentVolume = Mth.clamp(initialVolume, 0.0F, 1.0F);
            this.targetVolume = this.currentVolume;
            this.volume = this.currentVolume;
        }

        @Override
        public void tick() {
            if (this.fadeTicksRemaining > 0) {
                this.currentVolume += (this.targetVolume - this.currentVolume) / this.fadeTicksRemaining;
                this.fadeTicksRemaining--;
                this.volume = Mth.clamp(this.currentVolume, 0.0F, 1.0F);
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private void fadeTo(float targetVolume, int fadeTicks) {
            this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
            this.fadeTicksRemaining = Math.max(1, fadeTicks);
        }

        private boolean isFading() {
            return this.fadeTicksRemaining > 0;
        }

        private void stopSound() {
            this.stop();
        }
    }
}
