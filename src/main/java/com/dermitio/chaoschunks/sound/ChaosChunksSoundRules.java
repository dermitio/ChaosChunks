package com.dermitio.chaoschunks.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

// =========
// Server-side sound trigger rules evaluated by the sound ticker //
// =========
public final class ChaosChunksSoundRules {

    private static final List<BiomePeriodicRule> BIOME_PERIODIC_RULES = new ArrayList<>();

    private ChaosChunksSoundRules() {}

    // =========
    // Registers built-in periodic biome sound rules //
    // =========
    public static void registerAll() {
        registerBiomePeriodic(
                "void_ambient",
                ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:the_void")),
                ChaosChunksSounds.VOID_AMBIENT,
                20 * 30,      
                20 * 60 * 5,  
                0.5f,         
                0.2f,
                1.0f,
                SoundSource.PLAYERS,
                (player, level) -> true
        );
    }

    public static void registerBiomePeriodic(
            String id,
            ResourceKey<Biome> biome,
            Supplier<SoundEvent> sound,
            int checkIntervalTicks,
            int cooldownTicks,
            float chance,
            float volume,
            float pitch,
            SoundSource source,
            BiPredicate<ServerPlayer, ServerLevel> extraCondition
    ) {
        BIOME_PERIODIC_RULES.add(new BiomePeriodicRule(
                id, biome, sound, checkIntervalTicks, cooldownTicks,
                chance, volume, pitch, source, extraCondition
        ));
    }

    public static List<BiomePeriodicRule> biomePeriodicRules() {
        return BIOME_PERIODIC_RULES;
    }

    public record BiomePeriodicRule(
            String id,
            ResourceKey<Biome> biome,
            Supplier<SoundEvent> sound,
            int checkIntervalTicks,
            int cooldownTicks,
            float chance,
            float volume,
            float pitch,
            SoundSource source,
            BiPredicate<ServerPlayer, ServerLevel> extraCondition
    ) {}
}
