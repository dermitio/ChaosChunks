package com.dermitio.chaoschunks.server.config;

import com.dermitio.chaoschunks.data.world.ChaosChunksData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// =========
// Server-side worldgen defaults used before a world has saved ChaosChunks data //
// =========
public final class ChaosChunksServerWorldgenConfig {

    private static final Path DEFAULT_FILE = Paths.get("config/chaoschunks-server-worldgen.json");
    private static ServerWorldgenConfig CONFIG = new ServerWorldgenConfig();

    private ChaosChunksServerWorldgenConfig() {}

    public static final class ServerWorldgenConfig {
        public boolean enableOnFirstWorldCreation = false;
        public int regionX = 1;
        public int regionZ = 1;
        public String globalBiomes = "";
        public String defaultDimensionMode = "ON";
        public String regionSeedMode = "WORLD_SEED";
        public String terrainProfileMode = "NORMAL";
        public Map<String, String> dimensionModes = new HashMap<>();
        public Map<String, String> dimensionBiomes = new HashMap<>();
        public Map<String, Long> dimensionSeedRandomizers = new HashMap<>();
        public Map<String, Long> dimensionTerrainRandomizers = new HashMap<>();
    }

    public static void load() {
        CONFIG = load(DEFAULT_FILE);
        applyOverrides(CONFIG);
        CONFIG = sanitize(CONFIG);
    }

    public static void save() {
        save(DEFAULT_FILE, CONFIG);
    }

    public static ServerWorldgenConfig current() {
        return copy(CONFIG);
    }

    public static Path defaultPath() {
        return DEFAULT_FILE;
    }

    public static ServerWorldgenConfig load(Path file) {
        ServerWorldgenConfig loaded = new ServerWorldgenConfig();

        try {
            if (Files.exists(file)) {
                Gson gson = new Gson();
                ServerWorldgenConfig parsed = gson.fromJson(Files.readString(file), ServerWorldgenConfig.class);
                if (parsed != null) loaded = parsed;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sanitize(loaded);
    }

    public static void save(Path file, ServerWorldgenConfig config) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, gson.toJson(sanitize(config)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void importFrom(Path file) {
        CONFIG = load(file);
        applyOverrides(CONFIG);
        CONFIG = sanitize(CONFIG);
        save();
    }

    public static void exportTo(Path file) {
        save(file, CONFIG);
    }

    public static boolean applyFirstWorldDefaults(MinecraftServer server) {
        if (server.overworld() == null || !CONFIG.enableOnFirstWorldCreation) return false;

        ChaosChunksData data = ChaosChunksData.get(server.overworld().getDataStorage());
        if (hasWorldConfig(data)) return false;

        applyToData(data, CONFIG);
        applyMissingDimensionDefaults(server, data);
        data.enabled = true;
        data.setDirty();
        return true;
    }

    public static void applyMissingDimensionDefaults(MinecraftServer server, ChaosChunksData data) {
        if (!CONFIG.enableOnFirstWorldCreation) return;

        boolean dirty = false;
        String mode = normalizeMode(CONFIG.defaultDimensionMode);

        for (ServerLevel level : server.getAllLevels()) {
            String dim = stableId(level.dimension());

            if (!data.dimensionModes.containsKey(dim)) {
                data.dimensionModes.put(dim, mode);
                dirty = true;
            }

            if ("RANDOMIZED_REGION_SEED".equals(normalizeRegionSeedMode(CONFIG.regionSeedMode))
                    && !data.dimensionSeedRandomizers.containsKey(dim)) {
                data.dimensionSeedRandomizers.put(dim, newSeedRandomizerSalt());
                dirty = true;
            }

            if ("RANDOMIZED_TERRAIN_PROFILES".equals(normalizeTerrainProfileMode(CONFIG.terrainProfileMode))
                    && !data.dimensionTerrainRandomizers.containsKey(dim)) {
                data.dimensionTerrainRandomizers.put(dim, newSeedRandomizerSalt());
                dirty = true;
            }
        }

        if (dirty) data.setDirty();
    }

    public static ServerWorldgenConfig fromData(ChaosChunksData data) {
        ServerWorldgenConfig out = new ServerWorldgenConfig();
        out.enableOnFirstWorldCreation = data.enabled;
        out.regionX = data.regionX;
        out.regionZ = data.regionZ;
        out.globalBiomes = data.globalBiomes;
        out.dimensionBiomes.putAll(data.dimensionBiomes);
        out.dimensionModes.putAll(data.dimensionModes);
        out.dimensionSeedRandomizers.putAll(data.dimensionSeedRandomizers);
        out.dimensionTerrainRandomizers.putAll(data.dimensionTerrainRandomizers);
        out.defaultDimensionMode = "ON";
        out.regionSeedMode = data.dimensionSeedRandomizers.isEmpty() ? "WORLD_SEED" : "RANDOMIZED_REGION_SEED";
        out.terrainProfileMode = data.dimensionTerrainRandomizers.isEmpty() ? "NORMAL" : "RANDOMIZED_TERRAIN_PROFILES";
        return sanitize(out);
    }

    public static void applyToData(ChaosChunksData data, ServerWorldgenConfig config) {
        ServerWorldgenConfig safe = sanitize(config);

        data.enabled = safe.enableOnFirstWorldCreation;
        data.regionX = safe.regionX;
        data.regionZ = safe.regionZ;
        data.globalBiomes = safe.globalBiomes;

        data.dimensionBiomes.clear();
        data.dimensionModes.clear();
        data.dimensionSeedRandomizers.clear();
        data.dimensionTerrainRandomizers.clear();

        data.dimensionBiomes.putAll(safe.dimensionBiomes);
        data.dimensionModes.putAll(safe.dimensionModes);
        data.dimensionSeedRandomizers.putAll(safe.dimensionSeedRandomizers);
        data.dimensionTerrainRandomizers.putAll(safe.dimensionTerrainRandomizers);
        data.setDirty();
    }

    public static String normalizeMode(String raw) {
        String mode = normalize(raw);
        if ("SAFE".equals(mode) || "OFF".equals(mode)) return mode;
        return "ON";
    }

    public static String normalizeRegionSeedMode(String raw) {
        String mode = normalize(raw);
        if ("RANDOMIZED_REGION_SEED".equals(mode) || "RANDOMIZED".equals(mode) || "REGION".equals(mode)) {
            return "RANDOMIZED_REGION_SEED";
        }
        return "WORLD_SEED";
    }

    public static String normalizeTerrainProfileMode(String raw) {
        String mode = normalize(raw);
        if ("RANDOMIZED_TERRAIN_PROFILES".equals(mode)
                || "RANDOMIZED_TERRAIN".equals(mode)
                || "RANDOMIZED".equals(mode)
                || "TERRAIN".equals(mode)) {
            return "RANDOMIZED_TERRAIN_PROFILES";
        }
        return "NORMAL";
    }

    public static long newSeedRandomizerSalt() {
        long salt = ThreadLocalRandom.current().nextLong();
        return salt == 0L ? 1L : salt;
    }

    private static boolean hasWorldConfig(ChaosChunksData data) {
        return data.enabled
                || data.regionX != 1
                || data.regionZ != 1
                || (data.globalBiomes != null && !data.globalBiomes.isBlank())
                || !data.dimensionBiomes.isEmpty()
                || !data.dimensionModes.isEmpty()
                || !data.dimensionSeedRandomizers.isEmpty()
                || !data.dimensionTerrainRandomizers.isEmpty();
    }

    private static ServerWorldgenConfig sanitize(ServerWorldgenConfig in) {
        ServerWorldgenConfig out = new ServerWorldgenConfig();
        if (in == null) return out;

        out.enableOnFirstWorldCreation = in.enableOnFirstWorldCreation;
        out.regionX = Math.max(1, Math.min(512, in.regionX));
        out.regionZ = Math.max(1, Math.min(512, in.regionZ));
        out.globalBiomes = in.globalBiomes == null ? "" : in.globalBiomes.trim();
        out.defaultDimensionMode = normalizeMode(in.defaultDimensionMode);
        out.regionSeedMode = normalizeRegionSeedMode(in.regionSeedMode);
        out.terrainProfileMode = normalizeTerrainProfileMode(in.terrainProfileMode);

        if (in.dimensionBiomes != null) {
            in.dimensionBiomes.forEach((k, v) -> {
                if (k != null && !k.isBlank()) out.dimensionBiomes.put(k.trim(), v == null ? "" : v.trim());
            });
        }

        if (in.dimensionModes != null) {
            in.dimensionModes.forEach((k, v) -> {
                if (k != null && !k.isBlank()) out.dimensionModes.put(k.trim(), normalizeMode(v));
            });
        }

        if (in.dimensionSeedRandomizers != null) {
            in.dimensionSeedRandomizers.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && v != 0L) out.dimensionSeedRandomizers.put(k.trim(), v);
            });
        }

        if (in.dimensionTerrainRandomizers != null) {
            in.dimensionTerrainRandomizers.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && v != 0L) out.dimensionTerrainRandomizers.put(k.trim(), v);
            });
        }

        return out;
    }

    private static ServerWorldgenConfig copy(ServerWorldgenConfig in) {
        ServerWorldgenConfig out = new ServerWorldgenConfig();
        out.enableOnFirstWorldCreation = in.enableOnFirstWorldCreation;
        out.regionX = in.regionX;
        out.regionZ = in.regionZ;
        out.globalBiomes = in.globalBiomes;
        out.defaultDimensionMode = in.defaultDimensionMode;
        out.regionSeedMode = in.regionSeedMode;
        out.terrainProfileMode = in.terrainProfileMode;
        out.dimensionBiomes.putAll(in.dimensionBiomes);
        out.dimensionModes.putAll(in.dimensionModes);
        out.dimensionSeedRandomizers.putAll(in.dimensionSeedRandomizers);
        out.dimensionTerrainRandomizers.putAll(in.dimensionTerrainRandomizers);
        return out;
    }

    private static void applyOverrides(ServerWorldgenConfig config) {
        overrideBool(config, "chaoschunks.enabled", "CHAOSCHUNKS_ENABLED");
        overrideInt(config, "chaoschunks.regionX", "CHAOSCHUNKS_REGION_X", true);
        overrideInt(config, "chaoschunks.regionZ", "CHAOSCHUNKS_REGION_Z", false);
        overrideString(config, "chaoschunks.defaultDimensionMode", "CHAOSCHUNKS_DEFAULT_DIMENSION_MODE", value -> config.defaultDimensionMode = value);
        overrideString(config, "chaoschunks.regionSeedMode", "CHAOSCHUNKS_REGION_SEED_MODE", value -> config.regionSeedMode = value);
        overrideString(config, "chaoschunks.terrainProfileMode", "CHAOSCHUNKS_TERRAIN_PROFILE_MODE", value -> config.terrainProfileMode = value);
    }

    private static void overrideBool(ServerWorldgenConfig config, String property, String env) {
        String raw = overrideValue(property, env);
        if (raw != null) config.enableOnFirstWorldCreation = Boolean.parseBoolean(raw.trim());
    }

    private static void overrideInt(ServerWorldgenConfig config, String property, String env, boolean x) {
        String raw = overrideValue(property, env);
        if (raw == null) return;
        try {
            int value = Integer.parseInt(raw.trim());
            if (x) config.regionX = value;
            else config.regionZ = value;
        } catch (NumberFormatException ignored) {}
    }

    private static void overrideString(ServerWorldgenConfig config, String property, String env, java.util.function.Consumer<String> setter) {
        String raw = overrideValue(property, env);
        if (raw == null) return;
        setter.accept(raw);
    }

    private static String overrideValue(String property, String env) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) return value;
        value = System.getenv(env);
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String stableId(net.minecraft.resources.ResourceKey<?> key) {
        String s = String.valueOf(key);
        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }
        return s;
    }
}
