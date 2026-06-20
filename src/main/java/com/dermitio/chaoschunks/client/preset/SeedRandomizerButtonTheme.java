package com.dermitio.chaoschunks.client.preset;

import com.dermitio.chaoschunks.client.config.ChaosChunksUiEventConfig;

import java.time.LocalDate;

// =========
// Resolves date-based seed randomizer button colors from client UI event config //
// =========
public final class SeedRandomizerButtonTheme {

    // C O L O R S ! ! !
    public static final long DEFAULT_FRAME_MS = 120L;
    public static final int[] DEFAULT_RAINBOW = {
        0xFFFF4040,
        0xFFFFA000,
        0xFFFFFF40,
        0xFF40FF40,
        0xFF40A0FF,
        0xFFC040FF,
    };

    private static final Theme APRIL_1 = new Theme(
        new int[] { 0xFF000000 },
        DEFAULT_FRAME_MS
    );
    private static final Theme A_LEADER = new Theme(
        new int[] {
            0xFF000000,
            0xFFFFFFFF,
            0xFFE02020,
            0xFFFFFFFF,
            0xFF000000,
        },
        220L
    );
    private static final Theme HALLOWEEN = new Theme(
        new int[] { 0xFF8B2CFF, 0xFF071A3D, 0xFFFFFFFF, 0xFF071A3D },
        300L
    );
    private static final Theme NEW_YEAR = new Theme(
        new int[] { 0xFFFFFFFF, 0xFFFFD84A },
        420L
    );
    private static final Theme TRANS = new Theme(
        new int[] {
            0xFF5BCEFA,
            0xFFF5A9B8,
            0xFFFFFFFF,
            0xFFF5A9B8,
            0xFF5BCEFA,
        },
        DEFAULT_FRAME_MS
    );
    private static final Theme STRAIGHT = new Theme(
        new int[] {
            0xFF000000,
            0xFFFFFFFF,
            0xFF000000,
            0xFFFFFFFF,
            0xFF000000,
            0xFFFFFFFF,
        },
        220L
    );
    private static final Theme LESBIAN = new Theme(
        new int[] {
            0xFFD52D00,
            0xFFFF9A56,
            0xFFFFFFFF,
            0xFFD362A4,
            0xFFA30262,
        },
        DEFAULT_FRAME_MS
    );

    private SeedRandomizerButtonTheme() {}

    public record Theme(int[] colors, long frameMs) {}

    public static Theme activeTheme() {
        return activeTheme(LocalDate.now());
    }

    static Theme activeTheme(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (
            month == 4 &&
            day == 1 &&
            ChaosChunksUiEventConfig.isEnabled("april_1")
        ) return APRIL_1;
        if (
            month == 11 &&
            day == 10 &&
            ChaosChunksUiEventConfig.isEnabled("a_leader")
        ) return A_LEADER;
        if (
            month == 10 &&
            day == 31 &&
            ChaosChunksUiEventConfig.isEnabled("halloween")
        ) return HALLOWEEN;
        if (
            month == 1 &&
            day == 1 &&
            ChaosChunksUiEventConfig.isEnabled("new_year")
        ) return NEW_YEAR;

        if (month == 6 && ChaosChunksUiEventConfig.isEnabled("pride_month")) {
            if (day == 1) return TRANS;
            if (day == 2) return STRAIGHT;
            if (day == 3) return LESBIAN;
        }

        return new Theme(DEFAULT_RAINBOW, DEFAULT_FRAME_MS);
    }

    public static Theme previewTheme(String key) {
        return switch (key) {
            case "pride_month" -> pridePreviewTheme(LocalDate.now());
            case "new_year" -> NEW_YEAR;
            case "halloween" -> HALLOWEEN;
            case "a_leader" -> A_LEADER;
            case "april_1" -> APRIL_1;
            default -> new Theme(DEFAULT_RAINBOW, DEFAULT_FRAME_MS);
        };
    }

    private static Theme pridePreviewTheme(LocalDate date) {
        return switch (date.getDayOfMonth()) {
            case 1 -> TRANS;
            case 2 -> STRAIGHT;
            case 3 -> LESBIAN;
            default -> TRANS;
        };
    }

    public static Theme[] pridePreviewThemes() {
        return new Theme[] { TRANS, STRAIGHT, LESBIAN };
    }
}
