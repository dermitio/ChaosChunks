package com.dermitio.chaoschunks.server;

import com.dermitio.chaoschunks.data.ChaosChunksData;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import com.dermitio.chaoschunks.mixin.NoiseBasedChunkGeneratorAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import net.minecraft.network.chat.MutableComponent;


public final class ChaosChunksNotices {

    private static final Logger LOGGER = LogUtils.getLogger();


    private static final List<ModCountRule> MODCOUNT_RULES = new ArrayList<>();
    private static final List<ModRule> MOD_RULES = new ArrayList<>();
    private static final List<AdvRule> ADV_RULES = new ArrayList<>();

    private static volatile boolean INITED = false;
    private static volatile boolean MODRULES_RAN = false;

    private static Component JOIN_MESSAGE =
        Component.literal("ChaosChunks world active.");

    private ChaosChunksNotices() {}

    public static void init() {
        if (INITED) return;
        INITED = true;

        NeoForge.EVENT_BUS.addListener(ChaosChunksNotices::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ChaosChunksNotices::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ChaosChunksNotices::onAdvancementEarn);
        runModRulesOnceNow();
    }

public static MutableComponent msg(String text, ChatFormatting... styles) {
    return Component.literal(text).withStyle(styles);
}
    // ---------- public API (rules) ----------
    public static void setJoinMessage(Component msg) {
    if (msg != null) {
        JOIN_MESSAGE = msg;
    }
}

    public static void registerModCountLog(IntPredicate predicate, Component logLine) {
        MODCOUNT_RULES.add(new ModCountRule(predicate, logLine));
    }

    public static void registerModLoadedLog(String modId, Component logLine) {
        MOD_RULES.add(ModRule.whenAllLoaded(List.of(modId), logLine));
    }

    public static void registerAnyModLoadedLog(Collection<String> modIds, Component logLine) {
        MOD_RULES.add(ModRule.whenAnyLoaded(modIds, logLine));
    }

    public static void registerAllModsLoadedLog(Collection<String> modIds, Component logLine) {
        MOD_RULES.add(ModRule.whenAllLoaded(modIds, logLine));
    }

    public static void registerAdvancementChat(String advancementId, Component chatLine) {
        ADV_RULES.add(new AdvRule(Identifier.parse(advancementId), chatLine));
    }

    // ---------- output ----------
    private static void logInfo(String msg) {
        LOGGER.info("[ChaosChunks] {}", msg);
    }

private static ChatFormatting PREFIX_COLOR = ChatFormatting.LIGHT_PURPLE;

private static Component prefix() {
    return Component.literal("[ChaosChunks] ")
            .withStyle(PREFIX_COLOR);
}

private static Component formatted(String text, ChatFormatting... styles) {
    return Component.literal(text).withStyle(styles);
}

private static void tell(ServerPlayer player, Component message) {
    player.sendSystemMessage(prefix().copy().append(message));
}

    // ---------- mod helpers ----------
    private static int modCount() {
        ModList list = ModList.get();
        return list == null ? -1 : list.size();
    }

    private static boolean isLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    private static void runModRulesOnceNow() {
    if (MODRULES_RAN) return;
    MODRULES_RAN = true;

    int c = modCount();
    if (c >= 0) {
        for (ModCountRule rule : MODCOUNT_RULES) {
            if (!rule.predicate().test(c)) continue;
            logInfo(rule.message().getString()
        .replace("%MODCOUNT%", Integer.toString(c)));
            break;
        }
    }

    for (ModRule rule : MOD_RULES) {
        try { rule.evaluateAndLog(); } catch (Throwable ignored) {}
    }
}
    // ---------- chaos world gating ----------
private static boolean isChaosWorld(MinecraftServer server) {
    try {
        if (server == null || server.overworld() == null) return false;

        var level = server.overworld();
        var gen = level.getChunkSource().getGenerator();

        if (!((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc)) return false;
        return acc.chaoschunks$getBiomeSource() instanceof com.dermitio.chaoschunks.worldgen.ChaosBiomeSource;
    } catch (Throwable ignored) {
        return false;
    }
}

    // ---------- models ----------
    private record ModCountRule(IntPredicate predicate, Component message) {}

    private interface ModRule {
    void evaluateAndLog();

    static ModRule whenAnyLoaded(Collection<String> ids, Component message) {
        return () -> {
            for (String id : ids) {
                if (isLoaded(id)) {
                    logInfo(message.getString());
                    return;
                }
            }
        };
    }

    static ModRule whenAllLoaded(Collection<String> ids, Component message) {
        return () -> {
            for (String id : ids) {
                if (!isLoaded(id)) return;
            }
            logInfo(message.getString());
        };
    }
}

    private record AdvRule(Identifier id, Component message) {}

    // ---------- events ----------
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (MODRULES_RAN) return;
        MODRULES_RAN = true;

        int c = modCount();
        if (c >= 0) {
            for (ModCountRule rule : MODCOUNT_RULES) {
                if (!rule.predicate().test(c)) continue;
                logInfo(rule.message().getString().replace("%MODCOUNT%", Integer.toString(c)));
                break;
            }
        }

        for (ModRule rule : MOD_RULES) {
            try { rule.evaluateAndLog(); } catch (Throwable ignored) {}
        }
    }

private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;

    MinecraftServer server = player.level().getServer();
    if (server == null) return;

    // only greet when the current server world is actually a ChaosChunks world
    if (!isChaosWorld(server)) return;

    tell(player, JOIN_MESSAGE);
}

    private static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

MinecraftServer server = player.level().getServer();
if (server == null) return;

        var adv = event.getAdvancement();
        if (adv == null) return;

        Identifier id = adv.id();
        for (AdvRule rule : ADV_RULES) {
            if (!rule.id().equals(id)) continue;
            tell(player, rule.message());
            return;
        }
    }
}
