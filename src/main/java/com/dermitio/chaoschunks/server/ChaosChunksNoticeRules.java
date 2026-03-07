package com.dermitio.chaoschunks.server;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import static net.minecraft.ChatFormatting.*;
import static com.dermitio.chaoschunks.server.ChaosChunksNotices.msg;
import java.util.List;

public final class ChaosChunksNoticeRules {

    private ChaosChunksNoticeRules() {}




// usable options for text format

// BLACK
// DARK_BLUE
// DARK_GREEN
// DARK_AQUA
// DARK_RED
// DARK_PURPLE
// GOLD
// GRAY
// DARK_GRAY
// BLUE
// GREEN
// AQUA
// RED
// LIGHT_PURPLE
// YELLOW
// WHITE
// BOLD
// ITALIC
// UNDERLINE
// STRIKETHROUGH
// OBFUSCATED


    // ---------- rule registration ----------
public static void registerAll() {

    ChaosChunksNotices.registerModCountLog(
        c -> c < 25,
        msg("wow quite empty in here...", GRAY, ITALIC)
    );

    ChaosChunksNotices.registerModCountLog(
        c -> c >= 25 && c <= 70,
        msg("Have you ever heard of social anxiety?", YELLOW)
    );

    ChaosChunksNotices.registerModCountLog(
        c -> c > 70,
        msg("SOMEONE HELP ME! I AM BARELY BREATHING...", RED, BOLD)
    );

    ChaosChunksNotices.registerModLoadedLog(
        "gaia_dimension",
        msg("the dimension that shared the first experience.", AQUA)
    );

    ChaosChunksNotices.registerModLoadedLog(
        "quark",
        msg("OMG, may I get an autograph?", AQUA, BOLD)
    );

    ChaosChunksNotices.registerModLoadedLog(
        "thaumcraft",
        msg("once the ruler of all now back to rekindle its flame. welcome back your majesty.",
            DARK_PURPLE, ITALIC, UNDERLINE)
    );

    ChaosChunksNotices.registerAnyModLoadedLog(
        List.of("sodium", "iridium", "lithium", "krypton"),
        msg("oh god its the chemistry class.", DARK_GREEN)
    );

    ChaosChunksNotices.registerModLoadedLog(
        "embeddium",
        msg("math class everyone... yay...", WHITE, ITALIC)
    );

    ChaosChunksNotices.registerAnyModLoadedLog(
        List.of("oculus", "iris", "iris_shaders"),
        msg("looks better I guess.", WHITE)
    );

    ChaosChunksNotices.registerAdvancementChat(
        "minecraft:nether/explore_nether",
        msg("Was it harder or easier for you? ",
            GOLD)
            .append(msg("or even if it was hot at all.", DARK_RED, BOLD))
    );

    ChaosChunksNotices.registerAdvancementChat(
        "minecraft:adventure/adventuring_time",
        msg("All around a few chunks.", GREEN)
    );

    ChaosChunksNotices.registerAdvancementChat(
        "minecraft:story/enter_the_end",
        msg("Does the end really matter if its not the end at all?",
            DARK_GRAY, ITALIC)
    );

    ChaosChunksNotices.setJoinMessage(
        msg("Chaos ", LIGHT_PURPLE)
    .append(msg("Chaos ", LIGHT_PURPLE, ITALIC))
    .append(msg("Chaos!", LIGHT_PURPLE, BOLD, ITALIC))
    );
}
}
