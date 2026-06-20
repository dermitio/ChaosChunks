package com.dermitio.chaoschunks.sound;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.dermitio.chaoschunks.server.sound.ChaosChunksPlayerSoundPrefs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChaosChunksSoundTicker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean INITED = false;
    private static long tickCounter = 0L;

    // player UUID -> (rule id -> last played tick)
    private static final Map<UUID, Map<String, Long>> LAST_PLAYED = new ConcurrentHashMap<>();

    private ChaosChunksSoundTicker() {}

    public static void init() {
        if (INITED) return;
        INITED = true;

        NeoForge.EVENT_BUS.addListener(ChaosChunksSoundTicker::onServerTick);
    }
    

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;

        if (!ChaosChunksWorldState.isChaosWorld(server)) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;

            for (var rule : ChaosChunksSoundRules.biomePeriodicRules()) {
                if (rule.checkIntervalTicks() <= 0) continue;
                if (tickCounter % rule.checkIntervalTicks() != 0) continue;

                LOGGER.debug("[ChaosChunks] Sound check: rule={} player={} tick={}",
                        rule.id(), player.getScoreboardName(), tickCounter);

                var biomeKey = level.getBiome(player.blockPosition()).unwrapKey();
                if (biomeKey.isEmpty() || !biomeKey.get().equals(rule.biome())) {
                    LOGGER.debug("[ChaosChunks] Sound skipped: rule={} player={} biome mismatch",
                            rule.id(), player.getScoreboardName());
                    continue;
                }

                Map<String, Long> playerSounds =
                        LAST_PLAYED.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());

                Long lastPlayed = playerSounds.get(rule.id());
                boolean onCooldown = lastPlayed != null && (tickCounter - lastPlayed) < rule.cooldownTicks();

                if (onCooldown) {
                    long sinceLast = tickCounter - lastPlayed;
                    LOGGER.debug("[ChaosChunks] Sound skipped: rule={} player={} cooldown {}/{} ticks",
                            rule.id(), player.getScoreboardName(), sinceLast, rule.cooldownTicks());
                    continue;
                }

                if (!rule.extraCondition().test(player, level)) {
                    LOGGER.debug("[ChaosChunks] Sound skipped: rule={} player={} extra condition failed",
                            rule.id(), player.getScoreboardName());
                    continue;
                }

                float roll = level.getRandom().nextFloat();
                if (roll >= rule.chance()) {
                    LOGGER.debug("[ChaosChunks] Sound skipped: rule={} player={} rng={} chance={}",
                            rule.id(), player.getScoreboardName(), roll, rule.chance());
                    continue;
                }

                var sound = rule.sound().get();
                if (sound == null) {
                    LOGGER.error("[ChaosChunks] Sound missing/unbound for rule={}", rule.id());
                    continue;
                }

                float randomPitch = 0.9f + (level.getRandom().nextFloat() * 0.2f);
                String key = sound.location().getPath();
                if (!ChaosChunksPlayerSoundPrefs.isEnabled(player.getUUID(), key)) {
                    LOGGER.debug("[ChaosChunks] Sound skipped: rule={} player={} disabled in prefs key={}",
                            rule.id(), player.getScoreboardName(), key);
                    continue;
                }

                level.playSound(
                        null,
                        player.blockPosition(),
                        sound,
                        rule.source(),
                        rule.volume(),
                        randomPitch
                );

                playerSounds.put(rule.id(), tickCounter);

                LOGGER.debug("[ChaosChunks] Sound played: rule={} player={} volume={} pitch={}",
                        rule.id(), player.getScoreboardName(), rule.volume(), randomPitch);
            }
        }
    }
}
