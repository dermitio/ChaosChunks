package com.dermitio.chaoschunks.server.runtime;

import com.dermitio.chaoschunks.data.world.ChaosChunksData;
import com.dermitio.chaoschunks.mixin.NoiseBasedChunkGeneratorAccessor;
import com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig;
import com.dermitio.chaoschunks.server.config.ChaosChunksServerWorldgenConfig;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosChunkGenerator;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosGeneratorFactory;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosRegionSeed;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.dermitio.chaoschunks.data.catalog.ChaosBiomeParsing;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// =========
// Runtime patcher that applies Chaos biome sources to configured server dimensions //
// =========
public final class ChaosChunksRuntimeApplier {

    // ** Logger used for runtime patch reporting **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Tracks the last-applied configuration signature per server and dimension **
    private static final ConcurrentHashMap<String, String> APPLIED_SIGNATURE = new ConcurrentHashMap<>();

    // ** Keeps each dimension's pre-Chaos generator so OFF can restore custom dimensions without hardcoded rules **
    private static final ConcurrentHashMap<String, ChunkGenerator> ORIGINAL_GENERATORS = new ConcurrentHashMap<>();

    // ** Keeps biome sources for generators that can only be patched through ChunkGenerator.biomeSource **
    private static final ConcurrentHashMap<String, BiomeSource> ORIGINAL_BIOME_SOURCES = new ConcurrentHashMap<>();

    // ** Tracks loaded levels already seen by the late-dimension scan **
    private static final Set<String> SEEN_LEVELS = ConcurrentHashMap.newKeySet();

    // ** Prevents instantiation since this class only contains static hooks **
    private ChaosChunksRuntimeApplier() {}

    // ** Converts a ResourceKey to a stable identifier string **
    private static String stableId(ResourceKey<?> key) {
        String s = String.valueOf(key);
        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }
        return s;
    }

    // ** Builds a unique map key for tracking applied configs per server and dimension **
    private static String stateKey(MinecraftServer server, String dimId) {
        return System.identityHashCode(server) + "|" + dimId;
    }

    // ** Normalizes strings for signature comparisons **
    private static String norm(String s) {
        return (s == null) ? "" : s.trim();
    }

    private record RuntimeSettings(
            int regionX,
            int regionZ,
            String globalBiomes,
            Map<String, String> dimensionBiomes,
            Map<String, String> dimensionModes,
            Map<String, Long> dimensionSeedRandomizers,
            Map<String, Long> dimensionTerrainRandomizers
    ) {}

    // =========
    // Commits pending world-creation settings and applies them to loaded levels //
    // =========
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();

        var pending = ChaosChunksPendingConfig.consume();
        LOGGER.info("[ChaosChunks] onServerStarted pending={}", pending != null);
// trying to fix default worlds here, hopefully.
if (pending != null && server.overworld() != null) {
    var data = ChaosChunksData.get(server.overworld().getDataStorage());

    data.enabled = true; // "this world is a ChaosChunks world"

    data.regionX = pending.regionX();
    data.regionZ = pending.regionZ();
    data.globalBiomes = pending.globalBiomes();

    data.dimensionBiomes.clear();
    data.dimensionModes.clear();
    data.dimensionSeedRandomizers.clear();
    data.dimensionTerrainRandomizers.clear();
    data.dimensionBiomes.putAll(pending.dimensionBiomes());
    data.dimensionModes.putAll(pending.dimensionModes());
    data.dimensionSeedRandomizers.putAll(pending.dimensionSeedRandomizers());
    data.dimensionTerrainRandomizers.putAll(pending.dimensionTerrainRandomizers());

    data.setDirty();
} else {
    boolean appliedServerConfig = ChaosChunksServerWorldgenConfig.applyFirstWorldDefaults(server);
    if (appliedServerConfig) {
        LOGGER.info("[ChaosChunks] Applied server worldgen config defaults from {}.", ChaosChunksServerWorldgenConfig.defaultPath());
    }
}

        for (ServerLevel level : server.getAllLevels()) {
            applyToLevel(server, level);
            SEEN_LEVELS.add(stateKey(server, stableId(level.dimension())));
        }

        com.dermitio.chaoschunks.data.catalog.ChaosChunksCatalog.writeFromResources(server.getResourceManager());
    }

    // ** Applies runtime biome source patch when a level loads **
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        applyToLevel(level.getServer(), level);
        SEEN_LEVELS.add(stateKey(level.getServer(), stableId(level.dimension())));
    }

    // ** Catches custom dimensions that become visible after the normal startup/load events **
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            String dimId = stableId(level.dimension());
            String key = stateKey(server, dimId);
            if (!SEEN_LEVELS.add(key)) continue;

            LOGGER.info("[ChaosChunks] Late-visible dimension detected: {}", dimId);
            applyToLevel(server, level);
        }
    }

    public static void applyToAllLevels(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            applyToLevel(server, level);
            SEEN_LEVELS.add(stateKey(server, stableId(level.dimension())));
        }
    }
        // ** Checks whether the biome set contains only the_void **
private static boolean isOnlyTheVoid(HolderSet<Biome> set) {
    if (set == null) return false;
    var list = set.stream().toList();
    if (list.size() != 1) return false;
// the void is here now.
    var keyOpt = list.get(0).unwrapKey();
    return keyOpt.isPresent() && "minecraft:the_void".equals(stableId(keyOpt.get()));
}

    // =========
    // Applies a ChaosBiomeSource to one level when config or seed state changes //
    // =========
private static void applyToLevel(MinecraftServer server, ServerLevel level) {
    ChunkGenerator gen = level.getChunkSource().getGenerator();
    String dimId = stableId(level.dimension());
    String key = stateKey(server, dimId);

    var pending = ChaosChunksPendingConfig.peek();
    boolean enabled = server.overworld() != null && ChaosChunksData.get(server.overworld().getDataStorage()).enabled;
    LOGGER.info("[ChaosChunks] applyToLevel: dim={}, generator={}, pending={}, enabled={}",
            dimId, gen.getClass().getName(), pending != null, enabled);

    if (!((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc)) {
        LOGGER.warn("[ChaosChunks] Skipping {}: generator {} does not expose a patchable biome source.",
                dimId, gen.getClass().getName());
        return;
    }

    BiomeSource currentSource = acc.chaoschunks$getBiomeSource();
    RuntimeSettings settings = runtimeSettings(server, currentSource);
    if (settings == null) return;

    if (gen instanceof NoiseBasedChunkGenerator noise) {
        applyNoiseGeneratorPath(server, level, gen, noise, currentSource, settings, dimId, key);
        return;
    }

    applyBiomeSourceOnlyPath(server, gen, acc, currentSource, settings, dimId, key);
}

private static RuntimeSettings runtimeSettings(MinecraftServer server, BiomeSource currentSource) {
    var pending = ChaosChunksPendingConfig.peek();
    if (pending != null) {
        // If pending exists, we are *explicitly* in ChaosChunks flow -> allow patching.
        return new RuntimeSettings(
                Math.max(1, pending.regionX()),
                Math.max(1, pending.regionZ()),
                pending.globalBiomes(),
                pending.dimensionBiomes(),
                pending.dimensionModes(),
                pending.dimensionSeedRandomizers(),
                pending.dimensionTerrainRandomizers()
        );
    }

    // No pending config -> only patch if this world is marked as a ChaosChunks world.
    if (server.overworld() == null) return null;
    ChaosChunksData data = ChaosChunksData.get(server.overworld().getDataStorage());

    // Migration: worlds made with older versions won't have enabled=true,
    // so if they look configured (or already use ChaosBiomeSource), enable them once.
    boolean looksConfigured =
            data.regionX != 1 || data.regionZ != 1 ||
            (data.globalBiomes != null && !data.globalBiomes.isBlank()) ||
            (data.dimensionBiomes != null && !data.dimensionBiomes.isEmpty()) ||
            (data.dimensionModes != null && !data.dimensionModes.isEmpty()) ||
            (data.dimensionSeedRandomizers != null && !data.dimensionSeedRandomizers.isEmpty()) ||
            (data.dimensionTerrainRandomizers != null && !data.dimensionTerrainRandomizers.isEmpty());

    if (!data.enabled) {
        if (looksConfigured || (currentSource instanceof ChaosBiomeSource)) {
            data.enabled = true;
            data.setDirty();
        } else {
            return null; // vanilla/normal preset world -> never patch
        }
    }

    ChaosChunksServerWorldgenConfig.applyMissingDimensionDefaults(server, data);
    return new RuntimeSettings(
            Math.max(1, data.regionX),
            Math.max(1, data.regionZ),
            data.globalBiomes,
            data.dimensionBiomes,
            data.dimensionModes,
            data.dimensionSeedRandomizers,
            data.dimensionTerrainRandomizers
    );
}

private static void applyNoiseGeneratorPath(
        MinecraftServer server,
        ServerLevel level,
        ChunkGenerator gen,
        NoiseBasedChunkGenerator noise,
        BiomeSource currentSource,
        RuntimeSettings settings,
        String dimId,
        String key
) {
    long seed = server.getWorldGenSettings().options().seed();

    if (!(gen instanceof ChaosChunkGenerator) && !(currentSource instanceof ChaosBiomeSource)) {
        ORIGINAL_GENERATORS.putIfAbsent(key, gen);
    }

    long seedRandomizer = seedRandomizerFor(settings.dimensionSeedRandomizers(), dimId);
    long terrainRandomizer = seedRandomizerFor(settings.dimensionTerrainRandomizers(), dimId);
    String modeStr = modeFor(settings, dimId);
    if ("OFF".equalsIgnoreCase(modeStr)) {
        restoreOriginalGeneration(server, level, noise, gen, currentSource, key, dimId);
        APPLIED_SIGNATURE.put(key, "OFF");
        return;
    }

    String effective = effectiveBiomeText(settings, dimId);

    String sig = "FULL|" + settings.regionX() + "|" + settings.regionZ() + "|" + seed + "|" + seedRandomizer + "|" + terrainRandomizer + "|" + modeStr.toUpperCase(Locale.ROOT) + "|" + norm(effective);
    String prev = APPLIED_SIGNATURE.put(key, sig);
    if (sig.equals(prev)) return;

    Registry<Biome> biomeReg = server.registryAccess().lookupOrThrow(Registries.BIOME);
    HolderSet<Biome> allowed = resolveAllowedBiomes(biomeReg, currentSource, modeStr, effective);

    BiomeSource originalSource = originalBiomeSource(currentSource);

    // ** Keeps feature placement compatible with the dimension's original biome source **
    HolderSet<Biome> featureAllowed = featureBiomesFromSource(currentSource, biomeReg);

    // ** Builds a fresh Chaos generator so every generation stage routes through Chaos seed handling **
    var newSource = new ChaosBiomeSource(seed, settings.regionX(), settings.regionZ(), allowed, featureAllowed, seedRandomizer, terrainRandomizer, originalSource);
    List<Holder<NoiseGeneratorSettings>> profiles = terrainRandomizer == 0L
            ? List.of(noise.generatorSettings())
            : terrainProfiles(server, noise.generatorSettings());
    List<FlatLevelGeneratorSettings> flatProfiles = terrainRandomizer == 0L
            ? List.of()
            : flatTerrainProfiles(server);
    var newGen = ChaosGeneratorFactory.replaceBiomeSource(noise, newSource, profiles, flatProfiles);
    try { newGen.validate(); } catch (Throwable ignored) {}

    boolean patched = swapChunkGenerator(level, gen, newGen);

    // ** Existing-world fallback path when generator field replacement is unavailable **
    if (!patched && ((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc2)) {
        acc2.chaoschunks$setBiomeSource(newSource);
        try { noise.validate(); } catch (Throwable ignored) {}
        try { gen.refreshFeaturesPerStep(); } catch (Throwable ignored) {}
        patched = true;
    }

    String eff = norm(effective);
    LOGGER.info("[ChaosChunks] Patched biome generator for {} (path=full, mode={}, rx={}, rz={}, seedRandomizer={}, terrainRandomizer={}, filter={}, selectionBiomes={}, featureBiomes={}, patched={})",
            dimId, modeStr, settings.regionX(), settings.regionZ(), seedRandomizer != 0L, terrainRandomizer != 0L, eff.isEmpty() ? "<default>" : eff, countBiomes(allowed), countBiomes(featureAllowed), patched);
}

private static void applyBiomeSourceOnlyPath(
        MinecraftServer server,
        ChunkGenerator gen,
        NoiseBasedChunkGeneratorAccessor acc,
        BiomeSource currentSource,
        RuntimeSettings settings,
        String dimId,
        String key
) {
    if (!(currentSource instanceof ChaosBiomeSource)) {
        ORIGINAL_BIOME_SOURCES.putIfAbsent(key, currentSource);
    }

    String modeStr = modeFor(settings, dimId);
    if ("OFF".equalsIgnoreCase(modeStr)) {
        restoreOriginalBiomeSourceOnly(gen, acc, currentSource, key, dimId);
        APPLIED_SIGNATURE.put(key, "OFF");
        return;
    }

    long seed = server.getWorldGenSettings().options().seed();
    String effective = effectiveBiomeText(settings, dimId);
    String sig = "BIOME_ONLY|" + settings.regionX() + "|" + settings.regionZ() + "|" + seed + "|" + modeStr.toUpperCase(Locale.ROOT) + "|" + norm(effective);
    String prev = APPLIED_SIGNATURE.put(key, sig);
    if (sig.equals(prev)) return;

    Registry<Biome> biomeReg = server.registryAccess().lookupOrThrow(Registries.BIOME);
    HolderSet<Biome> allowed = resolveAllowedBiomes(biomeReg, currentSource, modeStr, effective);
    HolderSet<Biome> featureAllowed = featureBiomesFromSource(currentSource, biomeReg);
    BiomeSource originalSource = originalBiomeSource(currentSource);

    var newSource = new ChaosBiomeSource(seed, settings.regionX(), settings.regionZ(), allowed, featureAllowed, 0L, originalSource);
    acc.chaoschunks$setBiomeSource(newSource);
    try { gen.refreshFeaturesPerStep(); } catch (Throwable ignored) {}

    String eff = norm(effective);
    LOGGER.info("[ChaosChunks] Patched biome source for {} (path=biome-only, generator={}, mode={}, rx={}, rz={}, seedRandomizer=false, filter={}, selectionBiomes={}, featureBiomes={})",
            dimId, gen.getClass().getName(), modeStr, settings.regionX(), settings.regionZ(), eff.isEmpty() ? "<default>" : eff, countBiomes(allowed), countBiomes(featureAllowed));
}

private static String modeFor(RuntimeSettings settings, String dimId) {
    Map<String, String> dimModes = settings.dimensionModes();
    if (dimModes == null) return "ON";

    String mode = dimModes.get(dimId);
    if (mode == null) mode = dimModes.get(ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID);
    return mode == null ? "ON" : mode;
}

private static String effectiveBiomeText(RuntimeSettings settings, String dimId) {
    Map<String, String> dimBiomes = settings.dimensionBiomes();
    String per = dimBiomes == null ? "" : dimBiomes.getOrDefault(dimId, "");
    return per == null || per.isBlank() ? settings.globalBiomes() : per;
}

private static HolderSet<Biome> resolveAllowedBiomes(
        Registry<Biome> biomeReg,
        BiomeSource currentSource,
        String modeStr,
        String effective
) {
    HolderSet<Biome> allowed;
    String eff = norm(effective);

    if ("SAFE".equalsIgnoreCase(modeStr)) {
        allowed = parseSafeBiomeSelection(biomeReg, currentSource, eff);
    } else if (!eff.isEmpty()) {
        allowed = parseBiomeSelection(biomeReg, eff);
    } else {
        allowed = HolderSet.direct(biomeReg.stream().map(biomeReg::wrapAsHolder).toList());
    }

    if (allowed == null || allowed.stream().findAny().isEmpty()) {
        allowed = safeFromExistingSource(currentSource, biomeReg);
    }

    return allowed;
}
// this entire class is something I do not want to ever touch ever again...
// but we all know I will be back here anyway.
private static void restoreOriginalBiomeSourceOnly(
        ChunkGenerator gen,
        NoiseBasedChunkGeneratorAccessor acc,
        BiomeSource currentSource,
        String key,
        String dimId
) {
    BiomeSource restored = ORIGINAL_BIOME_SOURCES.get(key);

    if (restored == null && currentSource instanceof ChaosBiomeSource chaos) {
        restored = chaos.originalBiomeSource().orElse(null);
    }

    if (restored == null || restored == currentSource) return;

    acc.chaoschunks$setBiomeSource(restored);
    try { gen.refreshFeaturesPerStep(); } catch (Throwable ignored) {}

    LOGGER.info("[ChaosChunks] Restored original biome source for {} (path=biome-only)", dimId);
}

    // =========
    // Restores the pre-Chaos generator when a dimension is set to OFF //
    // =========
private static void restoreOriginalGeneration(
        MinecraftServer server,
        ServerLevel level,
        NoiseBasedChunkGenerator noise,
        ChunkGenerator currentGen,
        BiomeSource currentSource,
        String key,
        String dimId
) {
    ChunkGenerator original = ORIGINAL_GENERATORS.get(key);
    ChunkGenerator restored = original;

    if (restored == null && !(currentSource instanceof ChaosBiomeSource)) {
        restored = ChaosGeneratorFactory.restoreNoiseGenerator(noise, currentSource);
    }

    if (restored == null && currentSource instanceof ChaosBiomeSource chaos) {
        restored = chaos.originalBiomeSource()
                .map(source -> ChaosGeneratorFactory.restoreNoiseGenerator(noise, source))
                .orElse(null);
    }

    if (restored == null) {
        restored = vanillaFallbackGenerator(server, noise, dimId);
    }

    if (restored == null || restored == currentGen) return;

    boolean restoredFields = swapChunkGenerator(level, currentGen, restored);
    if (!restoredFields && currentGen instanceof NoiseBasedChunkGenerator currentNoise
            && restored instanceof NoiseBasedChunkGenerator restoredNoise
            && ((Object) currentNoise instanceof NoiseBasedChunkGeneratorAccessor currentAcc)
            && ((Object) restoredNoise instanceof NoiseBasedChunkGeneratorAccessor restoredAcc)) {
        currentAcc.chaoschunks$setBiomeSource(restoredAcc.chaoschunks$getBiomeSource());
        try { currentNoise.validate(); } catch (Throwable ignored) {}
        try { currentNoise.refreshFeaturesPerStep(); } catch (Throwable ignored) {}
        restoredFields = true;
    }

    LOGGER.info("[ChaosChunks] Restored vanilla/custom generator for {} (restored={})", dimId, restoredFields);
}

    // ** Last-resort migration path for old saves created when vanilla dimensions were serialized as Chaos generators **
private static ChunkGenerator vanillaFallbackGenerator(MinecraftServer server, NoiseBasedChunkGenerator noise, String dimId) {
    try {
        BiomeSource source = switch (dimId) {
            case "minecraft:overworld" -> {
                Registry<MultiNoiseBiomeSourceParameterList> presets =
                        server.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
                yield MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            }
            case "minecraft:the_nether" -> {
                Registry<MultiNoiseBiomeSourceParameterList> presets =
                        server.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
                yield MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            }
            case "minecraft:the_end" -> {
                Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
                yield TheEndBiomeSource.create(biomes);
            }
            default -> null;
        };

        return source == null ? null : new NoiseBasedChunkGenerator(source, noise.generatorSettings());
    } catch (Throwable t) {
        LOGGER.debug("[ChaosChunks] Failed building vanilla fallback generator for {}: {}", dimId, t.toString());
        return null;
    }
}

private static BiomeSource originalBiomeSource(BiomeSource currentSource) {
    if (currentSource instanceof ChaosBiomeSource chaos) {
        return chaos.originalBiomeSource().orElse(null);
    }
    return currentSource;
}

    private static List<Holder<NoiseGeneratorSettings>> terrainProfiles(MinecraftServer server, Holder<NoiseGeneratorSettings> fallback) {
        java.util.LinkedHashMap<String, Holder<NoiseGeneratorSettings>> profiles = new java.util.LinkedHashMap<>();
        putTerrainProfile(profiles, fallback);

        try {
            Registry<NoiseGeneratorSettings> registry = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
            putTerrainProfile(profiles, registry, NoiseGeneratorSettings.OVERWORLD);
            putTerrainProfile(profiles, registry, NoiseGeneratorSettings.LARGE_BIOMES);
            putTerrainProfile(profiles, registry, NoiseGeneratorSettings.AMPLIFIED);
            putTerrainProfile(profiles, registry, NoiseGeneratorSettings.FLOATING_ISLANDS);
        } catch (Throwable t) {
            LOGGER.debug("[ChaosChunks] Failed collecting terrain profiles: {}", t.toString());
        }

        return List.copyOf(profiles.values());
    }

    private static List<FlatLevelGeneratorSettings> flatTerrainProfiles(MinecraftServer server) {
        java.util.LinkedHashMap<String, FlatLevelGeneratorSettings> profiles = new java.util.LinkedHashMap<>();

        try {
            Registry<FlatLevelGeneratorPreset> registry = server.registryAccess().lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.CLASSIC_FLAT);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.TUNNELERS_DREAM);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.WATER_WORLD);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.OVERWORLD);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.SNOWY_KINGDOM);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.BOTTOMLESS_PIT);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.DESERT);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.REDSTONE_READY);
            putFlatProfile(profiles, registry, FlatLevelGeneratorPresets.THE_VOID);
        } catch (Throwable t) {
            LOGGER.debug("[ChaosChunks] Failed collecting superflat terrain profiles: {}", t.toString());
        }

        return List.copyOf(profiles.values());
    }

    private static void putFlatProfile(
            Map<String, FlatLevelGeneratorSettings> profiles,
            Registry<FlatLevelGeneratorPreset> registry,
            ResourceKey<FlatLevelGeneratorPreset> key
    ) {
        try {
            Holder<FlatLevelGeneratorPreset> holder = registry.getOrThrow(key);
            FlatLevelGeneratorSettings settings = holder.value().settings();
            if (settings != null) {
                profiles.putIfAbsent(stableId(key), settings);
            }
        } catch (Throwable ignored) {}
    }

    private static void putTerrainProfile(
            Map<String, Holder<NoiseGeneratorSettings>> profiles,
            Registry<NoiseGeneratorSettings> registry,
            ResourceKey<NoiseGeneratorSettings> key
    ) {
        try {
            putTerrainProfile(profiles, registry.getOrThrow(key));
        } catch (Throwable ignored) {}
    }

    private static void putTerrainProfile(Map<String, Holder<NoiseGeneratorSettings>> profiles, Holder<NoiseGeneratorSettings> holder) {
        if (holder == null) return;
        String id = holder.unwrapKey()
                .map(ChaosChunksRuntimeApplier::stableId)
                .orElseGet(() -> "direct:" + System.identityHashCode(holder));
        profiles.putIfAbsent(id, holder);
    }

    private static long seedRandomizerFor(Map<String, Long> dimSeedRandomizers, String dimId) {
        if (dimSeedRandomizers == null) return 0L;
        Long salt = dimSeedRandomizers.get(dimId);
        if (salt == null) salt = dimSeedRandomizers.get(ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID);
        if (salt == null) return 0L;
        if (salt == ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS) {
            return defaultSeedRandomizerFor(dimId);
        }
        return salt;
    }

    private static long defaultSeedRandomizerFor(String dimId) {
        long h = 0x6A09E667F3BCC909L;
        for (int i = 0; i < dimId.length(); i++) {
            h ^= (long) dimId.charAt(i) * 0x9E3779B97F4A7C15L;
            h = ChaosRegionSeed.mix64(h);
        }

        h = ChaosRegionSeed.mix64(h);
        return h == 0L ? 1L : h;
    }
    // =========
    // Replaces cached chunk-generator references used by already-loaded levels //
    // =========
    private static boolean swapChunkGenerator(ServerLevel level, ChunkGenerator oldGen, ChunkGenerator newGen) {
        boolean changed = false;

        Object chunkSource = level.getChunkSource();
        changed |= replaceGeneratorFields(chunkSource, oldGen, newGen);

        Object chunkMap = findFirstFieldByTypeName(chunkSource, "net.minecraft.server.level.ChunkMap");
        if (chunkMap != null) {
            changed |= replaceGeneratorFields(chunkMap, oldGen, newGen);
        }

        return changed;
    }

    // ** Finds the first field on an object matching a fully qualified type name **
    private static Object findFirstFieldByTypeName(Object owner, String typeName) {
        for (Field f : getAllFields(owner.getClass())) {
            if (!f.getType().getName().equals(typeName)) continue;
            try {
                f.setAccessible(true);
                return f.get(owner);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ** Replaces generator references inside an object via reflection **
    private static boolean replaceGeneratorFields(Object target, ChunkGenerator oldGen, ChunkGenerator newGen) {
        boolean changed = false;

        for (Field f : getAllFields(target.getClass())) {
            if (!ChunkGenerator.class.isAssignableFrom(f.getType())) continue;

            try {
                f.setAccessible(true);
                Object cur = f.get(target);
                if (cur == oldGen) {
                    f.set(target, newGen);
                    changed = true;
                }
            } catch (Throwable t) {
                LOGGER.debug("[ChaosChunks] Failed swapping generator field {} on {}: {}",
                        f.getName(), target.getClass().getName(), t.toString());
            }
        }

        return changed;
    }

    // ** Collects all fields from a class hierarchy **
    private static List<Field> getAllFields(Class<?> cls) {
        List<Field> out = new ArrayList<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) out.add(f);
            c = c.getSuperclass();
        }
        return out;
    }

    // ** Builds a safe biome set from an existing biome source **
    private static HolderSet<Biome> featureBiomesFromSource(BiomeSource src, Registry<Biome> biomeReg) {
        if (src instanceof ChaosBiomeSource chaos) {
            List<Holder<Biome>> list = chaos.featureBiomes().toList();
            if (!list.isEmpty()) return HolderSet.direct(list);
        }

        return safeFromExistingSource(src, biomeReg);
    }

    // ** Builds a safe biome set from an existing biome source **
    private static HolderSet<Biome> safeFromExistingSource(BiomeSource src, Registry<Biome> biomeReg) {
        try {
            var set = src.possibleBiomes();
            if (set != null && !set.isEmpty()) {
                return HolderSet.direct(new ArrayList<>(set));
            }
        } catch (Throwable ignored) {}

        return HolderSet.direct(biomeReg.stream().map(biomeReg::wrapAsHolder).toList());
    }

    // ** Counts biome holders for diagnostics without failing generation if a holder set is odd **
    private static long countBiomes(HolderSet<Biome> set) {
        try {
            return (set == null) ? 0 : set.stream().count();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ** Parses SAFE biome text by starting with dimension biomes and adding explicit whitelist entries only **
private static HolderSet<Biome> parseSafeBiomeSelection(Registry<Biome> registry, BiomeSource source, String text) {
    var spec = ChaosBiomeParsing.parse(text);
    java.util.LinkedHashSet<Holder<Biome>> pool = new java.util.LinkedHashSet<>();

    HolderSet<Biome> safeBase = safeFromExistingSource(source, registry);
    safeBase.forEach(pool::add);

    addExplicitIncludes(registry, spec, pool);
    applyBlacklist(registry, spec, pool);
    removeDecorationUnsafe(pool);

    if (pool.isEmpty()) {
        return safeFromExistingSource(source, registry);
    }

    return HolderSet.direct(new java.util.ArrayList<>(pool));
}

    // ** Parses biome selection text into a HolderSet using registry IDs or tags **
private static HolderSet<Biome> parseBiomeSelection(Registry<Biome> registry, String text) {
    var spec = ChaosBiomeParsing.parse(text);

    java.util.LinkedHashSet<Holder<Biome>> pool = new java.util.LinkedHashSet<>();

    // ---------- INCLUDES ----------
    if (!spec.hasAnyIncludes()) {
        registry.stream().forEach(b -> pool.add(registry.wrapAsHolder(b)));
    } else {

        // include tags
        for (String tagStr : spec.includeTagIds()) {
            Identifier tagId = Identifier.tryParse(tagStr);
            if (tagId == null) continue;
            try {
                HolderSet<Biome> set = registry.getOrThrow(TagKey.create(Registries.BIOME, tagId));
                set.forEach(pool::add);
            } catch (Throwable ignored) {}
        }

        // include ids
        for (String idStr : spec.includeIds()) {
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;
            registry.get(ResourceKey.create(Registries.BIOME, id)).ifPresent(pool::add);
        }
    }

    applyBlacklist(registry, spec, pool);
    removeDecorationUnsafe(pool);

// ---------- EMPTY → ALL (then re-apply deny) ----------
if (pool.isEmpty()) {
    registry.stream().forEach(b -> pool.add(registry.wrapAsHolder(b)));

    applyBlacklist(registry, spec, pool);
    removeDecorationUnsafe(pool);
}

    return HolderSet.direct(new java.util.ArrayList<>(pool));
}

private static void addExplicitIncludes(
        Registry<Biome> registry,
        ChaosBiomeParsing.Spec spec,
        java.util.LinkedHashSet<Holder<Biome>> pool
) {
    for (String tagStr : spec.includeTagIds()) {
        Identifier tagId = Identifier.tryParse(tagStr);
        if (tagId == null) continue;
        try {
            HolderSet<Biome> set = registry.getOrThrow(TagKey.create(Registries.BIOME, tagId));
            set.forEach(pool::add);
        } catch (Throwable ignored) {}
    }

    for (String idStr : spec.includeIds()) {
        Identifier id = Identifier.tryParse(idStr);
        if (id == null) continue;
        registry.get(ResourceKey.create(Registries.BIOME, id)).ifPresent(pool::add);
    }
}

private static void applyBlacklist(
        Registry<Biome> registry,
        ChaosBiomeParsing.Spec spec,
        java.util.LinkedHashSet<Holder<Biome>> pool
) {
    java.util.HashSet<String> deny = new java.util.HashSet<>(spec.blacklistIds());

    for (String tagStr : spec.blacklistTagIds()) {
        Identifier tagId = Identifier.tryParse(tagStr);
        if (tagId == null) continue;
        try {
            HolderSet<Biome> set = registry.getOrThrow(TagKey.create(Registries.BIOME, tagId));
            set.forEach(h -> {
                var k = h.unwrapKey();
                if (k.isPresent()) deny.add(ChaosBiomeParsing.stableId(k.get()));
            });
        } catch (Throwable ignored) {}
    }

    if (!deny.isEmpty()) {
        pool.removeIf(h -> {
            var k = h.unwrapKey();
            return k.isPresent() && deny.contains(ChaosBiomeParsing.stableId(k.get()));
        });
    }
}

private static void removeDecorationUnsafe(java.util.LinkedHashSet<Holder<Biome>> pool) {
    pool.removeIf(h -> {
        if (isTheVoid(h)) return false;

        try {
            var feats = h.value().getGenerationSettings().features();
            return feats == null;
        } catch (Throwable t) {
            return false;
        }
    });
}

private static boolean isTheVoid(Holder<Biome> holder) {
    if (holder == null) return false;

    var keyOpt = holder.unwrapKey();
    return keyOpt.isPresent() && "minecraft:the_void".equals(stableId(keyOpt.get()));
}
}
