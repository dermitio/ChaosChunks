package com.dermitio.chaoschunks.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

// =========
// Client-side default values used when initializing new ChaosChunks world settings //
// =========
public final class ChaosChunksDefaultsConfig {

    private static final Path FILE = Paths.get("config/chaoschunks_defaults.json");

    private static Defaults DATA = new Defaults();

    private ChaosChunksDefaultsConfig() {}

    public enum DefaultDimensionMode {
        ON("ON"),
        SAFE("SAFE"),
        OFF("OFF");

        private final String label;

        DefaultDimensionMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum RegionSeedMode {
        WORLD_SEED("World Seed"),
        RANDOMIZED_REGION_SEED("Chaos Regions");

        private final String label;

        RegionSeedMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum TerrainProfileMode {
        NORMAL("Normal"),
        RANDOMIZED_TERRAIN_PROFILES("Randomized Terrain");

        private final String label;

        TerrainProfileMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final class Defaults {
        String defaultDimensionMode = DefaultDimensionMode.ON.name();
        String regionSeedMode = RegionSeedMode.WORLD_SEED.name();
        String terrainProfileMode = TerrainProfileMode.NORMAL.name();
        boolean experimentalWorldTypeRandomization = false;
    }

    public static DefaultDimensionMode defaultDimensionMode() {
        return parseEnum(DATA.defaultDimensionMode, DefaultDimensionMode.ON);
    }

    public static void setDefaultDimensionMode(DefaultDimensionMode mode) {
        DATA.defaultDimensionMode = (mode == null ? DefaultDimensionMode.ON : mode).name();
    }

    public static RegionSeedMode regionSeedMode() {
        return parseEnum(DATA.regionSeedMode, RegionSeedMode.WORLD_SEED);
    }

    public static void setRegionSeedMode(RegionSeedMode mode) {
        DATA.regionSeedMode = (mode == null ? RegionSeedMode.WORLD_SEED : mode).name();
    }

    public static TerrainProfileMode terrainProfileMode() {
        return parseEnum(DATA.terrainProfileMode, TerrainProfileMode.NORMAL);
    }

    public static void setTerrainProfileMode(TerrainProfileMode mode) {
        DATA.terrainProfileMode = (mode == null ? TerrainProfileMode.NORMAL : mode).name();
    }

    public static boolean experimentalWorldTypeRandomization() {
        return DATA.experimentalWorldTypeRandomization;
    }

    public static void setExperimentalWorldTypeRandomization(boolean enabled) {
        DATA.experimentalWorldTypeRandomization = enabled;
        if (!enabled) {
            DATA.terrainProfileMode = TerrainProfileMode.NORMAL.name();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(FILE, gson.toJson(DATA));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        DATA = new Defaults();

        try {
            if (!Files.exists(FILE)) return;

            Gson gson = new Gson();
            Defaults loaded = gson.fromJson(Files.readString(FILE), Defaults.class);
            if (loaded != null) DATA = sanitize(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Defaults sanitize(Defaults loaded) {
        Defaults out = new Defaults();
        out.defaultDimensionMode = parseEnum(loaded.defaultDimensionMode, DefaultDimensionMode.ON).name();
        out.regionSeedMode = parseEnum(loaded.regionSeedMode, RegionSeedMode.WORLD_SEED).name();
        out.terrainProfileMode = parseEnum(loaded.terrainProfileMode, TerrainProfileMode.NORMAL).name();
        out.experimentalWorldTypeRandomization = loaded.experimentalWorldTypeRandomization;
        if (!out.experimentalWorldTypeRandomization) {
            out.terrainProfileMode = TerrainProfileMode.NORMAL.name();
        }
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
