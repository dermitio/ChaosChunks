package com.dermitio.chaoschunks.client.hud;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksEffects;
import com.dermitio.chaoschunks.content.effects.FreshnessMobEffect;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// =========
// Renders transparent Freshness hearts for healing queued from recent damage //
// =========
public final class FreshnessHudOverlay {
    private static final Identifier FRESHNESS_FULL =
            Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "hud/heart/pooled_full");
    private static final Identifier FRESHNESS_HALF =
            Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "hud/heart/pooled_half");
    private static final Identifier FRESHNESS_RIGHT_HALF =
            Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "hud/heart/pooled_rhalf");
    private static final int HEART_ALPHA = ARGB.white(0.45F);

    private static final ArrayDeque<DamageRecord> RECENT_DAMAGE = new ArrayDeque<>();
    private static UUID lastPlayerId;
    private static float lastHealth = Float.NaN;
    private static boolean hadFreshness;
    private static long nextHealTick = Long.MAX_VALUE;

    private FreshnessHudOverlay() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) {
            RECENT_DAMAGE.clear();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) return;

        Player player = minecraft.player;
        long gameTime = player.level().getGameTime();
        MobEffectInstance freshness = player.getEffect(ChaosChunksEffects.FRESHNESS);
        syncObservedHealth(player, freshness, gameTime);
        if (freshness == null) return;

        float pendingHeal = pendingHeal(player, freshness.getAmplifier(), gameTime);
        if (pendingHeal <= 0.0F) return;

        renderQueuedHearts(event.getGuiGraphics(), player, pendingHeal);
    }

    private static void syncObservedHealth(Player player, MobEffectInstance freshness, long gameTime) {
        UUID playerId = player.getUUID();
        float health = player.getHealth();
        if (!playerId.equals(lastPlayerId)) {
            lastPlayerId = playerId;
            lastHealth = health;
            RECENT_DAMAGE.clear();
            hadFreshness = freshness != null;
            nextHealTick = hadFreshness ? gameTime + FreshnessMobEffect.HEAL_DELAY_TICKS : Long.MAX_VALUE;
            return;
        }

        boolean hasFreshness = freshness != null;
        if (hasFreshness && !hadFreshness) {
            prune(gameTime, true);
            nextHealTick = gameTime + FreshnessMobEffect.HEAL_DELAY_TICKS;
        } else if (!hasFreshness) {
            nextHealTick = Long.MAX_VALUE;
        }
        hadFreshness = hasFreshness;

        if (!Float.isNaN(lastHealth)) {
            float delta = lastHealth - health;
            if (delta > 0.0F) {
                RECENT_DAMAGE.addLast(new DamageRecord(gameTime, delta));
                if (hasFreshness) {
                    nextHealTick = gameTime + FreshnessMobEffect.HEAL_DELAY_TICKS;
                }
            } else if (delta < 0.0F) {
                removeRecoveredDamage(-delta, player);
            }
        }

        lastHealth = health;
        prune(gameTime, !hasFreshness);
    }

    private static float pendingHeal(Player player, int amplification, long gameTime) {
        if (gameTime >= nextHealTick) return 0.0F;
        prune(gameTime, false);

        float recentDamage = 0.0F;
        for (DamageRecord record : RECENT_DAMAGE) {
            recentDamage += record.damage();
        }

        float missingHealth = player.getMaxHealth() - player.getHealth();
        return Math.min(missingHealth, recentDamage * FreshnessMobEffect.recoveryMultiplier(amplification));
    }

    private static void renderQueuedHearts(GuiGraphicsExtractor graphics, Player player, float pendingHeal) {
        int currentHealthHalves = Mth.ceil(player.getHealth());
        int previewHalves = Mth.ceil(pendingHeal);
        if (previewHalves <= 0) return;

        float maxHealth = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
        int totalAbsorption = Mth.ceil(player.getAbsorptionAmount());
        int numHealthRows = Mth.ceil((maxHealth + totalAbsorption) / 2.0F / 10.0F);
        int healthRowHeight = Math.max(10 - (numHealthRows - 2), 3);
        int xLeft = graphics.guiWidth() / 2 - 91;
        int yLineBase = graphics.guiHeight() - 39;
        int startHeart = currentHealthHalves / 2;

        if ((currentHealthHalves & 1) == 1) {
            drawQueuedHeart(graphics, xLeft, yLineBase, healthRowHeight, startHeart, FRESHNESS_RIGHT_HALF);
            previewHalves--;
            startHeart++;
        }

        for (int half = 0; half < previewHalves; half += 2) {
            int heartIndex = startHeart + half / 2;
            boolean halfHeart = half + 1 == previewHalves;
            drawQueuedHeart(graphics, xLeft, yLineBase, healthRowHeight, heartIndex, halfHeart ? FRESHNESS_HALF : FRESHNESS_FULL);
        }
    }

    private static void drawQueuedHeart(
            GuiGraphicsExtractor graphics,
            int xLeft,
            int yLineBase,
            int healthRowHeight,
            int heartIndex,
            Identifier sprite
    ) {
        int x = xLeft + (heartIndex % 10) * 8;
        int y = yLineBase - (heartIndex / 10) * healthRowHeight;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 9, 9, HEART_ALPHA);
    }

    private static void removeRecoveredDamage(float healedHealth, Player player) {
        MobEffectInstance freshness = player.getEffect(ChaosChunksEffects.FRESHNESS);
        float multiplier = freshness == null ? 1.0F : FreshnessMobEffect.recoveryMultiplier(freshness.getAmplifier());
        float rawToRemove = healedHealth / multiplier;

        while (rawToRemove > 0.0F && !RECENT_DAMAGE.isEmpty()) {
            DamageRecord first = RECENT_DAMAGE.removeFirst();
            if (first.damage() > rawToRemove) {
                RECENT_DAMAGE.addFirst(new DamageRecord(first.gameTime(), first.damage() - rawToRemove));
                return;
            }
            rawToRemove -= first.damage();
        }
    }

    private static void prune(long gameTime, boolean removeExpired) {
        if (!removeExpired) return;

        long oldestAllowed = gameTime - FreshnessMobEffect.DAMAGE_MEMORY_TICKS;
        Iterator<DamageRecord> iterator = RECENT_DAMAGE.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().gameTime() < oldestAllowed) {
                iterator.remove();
            }
        }
    }

    private record DamageRecord(long gameTime, float damage) {}
}
