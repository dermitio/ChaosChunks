package com.dermitio.chaoschunks.content.effects;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksEffects;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// =========
// Delayed recovery effect that restores recent damage at reduced effectiveness //
// =========
public class FreshnessMobEffect extends MobEffect {
    public static final int DAMAGE_MEMORY_TICKS = 60;
    public static final int HEAL_DELAY_TICKS = 70;
    private static final Map<UUID, ArrayDeque<DamageRecord>> RECENT_DAMAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_HEAL_TICK = new ConcurrentHashMap<>();

    public FreshnessMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x55FFE6);
    }

    // =========
    // Records final health loss so Freshness can recover damage taken shortly before it was applied //
    // =========
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        float damage = event.getNewDamage();
        if (damage <= 0.0F || event.getEntity().level().isClientSide()) return;

        long gameTime = event.getEntity().level().getGameTime();
        UUID entityId = event.getEntity().getUUID();
        ArrayDeque<DamageRecord> records = RECENT_DAMAGE.computeIfAbsent(entityId, ignored -> new ArrayDeque<>());
        records.addLast(new DamageRecord(gameTime, damage));
        prune(records, gameTime, !event.getEntity().hasEffect(ChaosChunksEffects.FRESHNESS));

        if (event.getEntity().hasEffect(ChaosChunksEffects.FRESHNESS)) {
            NEXT_HEAL_TICK.put(entityId, gameTime + HEAL_DELAY_TICKS);
        }
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return true;

        ArrayDeque<DamageRecord> records = RECENT_DAMAGE.get(mob.getUUID());
        if (records == null) return true;

        long gameTime = level.getGameTime();
        Long nextHealTick = NEXT_HEAL_TICK.get(mob.getUUID());
        if (nextHealTick == null) {
            NEXT_HEAL_TICK.put(mob.getUUID(), gameTime + HEAL_DELAY_TICKS);
            return true;
        }

        if (gameTime < nextHealTick) return true;

        float queuedDamage = queuedDamage(records);
        float multiplier = recoveryMultiplier(amplification);
        float healAmount = Math.min(mob.getMaxHealth() - mob.getHealth(), queuedDamage * multiplier);
        removeQueuedDamage(records, multiplier <= 0.0F ? queuedDamage : healAmount / multiplier);

        if (records.isEmpty()) {
            RECENT_DAMAGE.remove(mob.getUUID(), records);
            NEXT_HEAL_TICK.remove(mob.getUUID());
        }

        if (healAmount > 0.0F) {
            mob.heal(healAmount);
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplification) {
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity mob, int amplifier) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        long gameTime = mob.level().getGameTime();
        ArrayDeque<DamageRecord> records = RECENT_DAMAGE.get(mob.getUUID());
        if (records != null) {
            prune(records, gameTime, true);
        }
        NEXT_HEAL_TICK.put(mob.getUUID(), gameTime + HEAL_DELAY_TICKS);
    }

    private static void prune(ArrayDeque<DamageRecord> records, long gameTime, boolean removeExpired) {
        if (!removeExpired) return;

        long oldestAllowed = gameTime - DAMAGE_MEMORY_TICKS;
        while (!records.isEmpty() && records.peekFirst().gameTime() < oldestAllowed) {
            records.removeFirst();
        }
    }

    public static float recoveryMultiplier(int amplification) {
        return Math.clamp((amplification + 1) * 0.25F, 0.0F, 1.0F);
    }

    private static float queuedDamage(ArrayDeque<DamageRecord> records) {
        float damage = 0.0F;
        for (DamageRecord record : records) {
            damage += record.damage();
        }
        return damage;
    }

    private static void removeQueuedDamage(ArrayDeque<DamageRecord> records, float damageToRemove) {
        while (damageToRemove > 0.0F && !records.isEmpty()) {
            DamageRecord first = records.removeFirst();
            if (first.damage() > damageToRemove) {
                records.addFirst(new DamageRecord(first.gameTime(), first.damage() - damageToRemove));
                return;
            }
            damageToRemove -= first.damage();
        }
    }

    private record DamageRecord(long gameTime, float damage) {}
}
