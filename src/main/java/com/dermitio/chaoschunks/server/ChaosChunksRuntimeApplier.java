package com.dermitio.chaoschunks.server;

import com.dermitio.chaoschunks.data.ChaosChunksData;
import com.dermitio.chaoschunks.mixin.NoiseBasedChunkGeneratorAccessor;
import com.dermitio.chaoschunks.worldgen.ChaosBiomeSource;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import com.dermitio.chaoschunks.data.ChaosBiomeParsing;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChaosChunksRuntimeApplier {

    // ** Logger used for runtime patch reporting **
    private static final Logger LOGGER = LogUtils.getLogger();

    // ** Tracks the last-applied configuration signature per server and dimension **
    private static final ConcurrentHashMap<String, String> APPLIED_SIGNATURE = new ConcurrentHashMap<>();

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

    // ** Applies pending config on server start and patches all loaded levels **
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();

        var pending = ChaosChunksPendingConfig.consume();
// trying to fix default worlds here, hopefully.
if (pending != null && server.overworld() != null) {
    var data = ChaosChunksData.get(server.overworld().getDataStorage());

    data.enabled = true; // "this world is a ChaosChunks world"

    data.regionX = pending.regionX();
    data.regionZ = pending.regionZ();
    data.globalBiomes = pending.globalBiomes();

    data.dimensionBiomes.clear();
    data.dimensionModes.clear();
    data.dimensionBiomes.putAll(pending.dimensionBiomes());
    data.dimensionModes.putAll(pending.dimensionModes());

    data.setDirty();
}

        for (ServerLevel level : server.getAllLevels()) {
            applyToLevel(server, level);
        }

        com.dermitio.chaoschunks.data.ChaosChunksCatalog.writeFromResources(server.getResourceManager());
    }

    // ** Applies runtime biome source patch when a level loads **
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        applyToLevel(level.getServer(), level);
    }
        // ** Checks whether the biome set contains only the_void **
private static boolean isOnlyTheVoid(HolderSet<Biome> set) {
    if (set == null) return false;
    var list = set.stream().toList();
    if (list.size() != 1) return false;

    var keyOpt = list.get(0).unwrapKey();
    return keyOpt.isPresent() && "minecraft:the_void".equals(stableId(keyOpt.get()));
}

    // ** Applies Chaos biome source to a level based on current config and signature state **
private static void applyToLevel(MinecraftServer server, ServerLevel level) {
    ChunkGenerator gen = level.getChunkSource().getGenerator();
    if (!(gen instanceof NoiseBasedChunkGenerator noise)) return;

    if (!((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc)) return;
    BiomeSource currentSource = acc.chaoschunks$getBiomeSource();

    var pending = ChaosChunksPendingConfig.peek();

    int rx;
    int rz;
    String global;
    Map<String, String> dimBiomes;
    Map<String, String> dimModes;

    if (pending != null) {
        // If pending exists, we are *explicitly* in ChaosChunks flow -> allow patching.
        rx = pending.regionX();
        rz = pending.regionZ();
        global = pending.globalBiomes();
        dimBiomes = pending.dimensionBiomes();
        dimModes = pending.dimensionModes();
    } else {
        // No pending config -> only patch if this world is marked as a ChaosChunks world.
        if (server.overworld() == null) return;
        ChaosChunksData data = ChaosChunksData.get(server.overworld().getDataStorage());

        // ---- HARD GATE: do nothing for vanilla worlds ----
        // Migration: worlds made with older versions won't have enabled=true,
        // so if they look configured (or already use ChaosBiomeSource), enable them once.
        boolean looksConfigured =
                data.regionX != 1 || data.regionZ != 1 ||
                (data.globalBiomes != null && !data.globalBiomes.isBlank()) ||
                (data.dimensionBiomes != null && !data.dimensionBiomes.isEmpty()) ||
                (data.dimensionModes != null && !data.dimensionModes.isEmpty());

        if (!data.enabled) {
            if (looksConfigured || (currentSource instanceof ChaosBiomeSource)) {
                data.enabled = true;
                data.setDirty();
            } else {
                return; // vanilla/normal preset world -> never patch
            }
        }

        rx = data.regionX;
        rz = data.regionZ;
        global = data.globalBiomes;
        dimBiomes = data.dimensionBiomes;
        dimModes = data.dimensionModes;
    }

    rx = Math.max(1, rx);
    rz = Math.max(1, rz);

    long seed = server.getWorldData().worldGenOptions().seed();

    String dimId = stableId(level.dimension());
    String modeStr = (dimModes == null) ? "ON" : dimModes.getOrDefault(dimId, "ON");
    if ("OFF".equalsIgnoreCase(modeStr)) return;

    String per = (dimBiomes == null) ? "" : dimBiomes.getOrDefault(dimId, "");
    String effective = (per == null || per.isBlank()) ? global : per;

    String sig = rx + "|" + rz + "|" + seed + "|" + modeStr.toUpperCase(Locale.ROOT) + "|" + norm(effective);
    String key = stateKey(server, dimId);
    String prev = APPLIED_SIGNATURE.put(key, sig);
    if (sig.equals(prev)) return;

    Registry<Biome> biomeReg = server.registryAccess().lookupOrThrow(Registries.BIOME);

    // ** Determines biome selection pool used for chaos generation **
    HolderSet<Biome> allowed;
    String eff = norm(effective);

    if (!eff.isEmpty()) {
        allowed = parseBiomeSelection(biomeReg, eff);
    } else if ("SAFE".equalsIgnoreCase(modeStr)) {
        allowed = safeFromExistingSource(currentSource, biomeReg);
    } else {
        allowed = HolderSet.direct(biomeReg.stream().map(biomeReg::wrapAsHolder).toList());
    }

    if (allowed == null || allowed.stream().findAny().isEmpty()) {
        allowed = safeFromExistingSource(currentSource, biomeReg);
    }

    // ** Determines feature pool used for placement ordering **
    HolderSet<Biome> featureAllowed = allowed;
    if (isOnlyTheVoid(allowed)) {
        featureAllowed = safeFromExistingSource(currentSource, biomeReg);
    }

    // ** Builds a fresh generator so feature caches match the new biome source **
    var newSource = new ChaosBiomeSource(seed, rx, rz, allowed, featureAllowed);

    var newGen = new NoiseBasedChunkGenerator(newSource, noise.generatorSettings());
    try { newGen.validate(); } catch (Throwable ignored) {}

    boolean swapped = swapChunkGenerator(level, gen, newGen);

    // ** Fallback path: directly replace biome source if generator swap fails **
    if (!swapped && ((Object) gen instanceof NoiseBasedChunkGeneratorAccessor acc2)) {
        acc2.chaoschunks$setBiomeSource(newSource);
        try { noise.validate(); } catch (Throwable ignored) {}
    }

    LOGGER.info("[ChaosChunks] Patched biome generator for {} (mode={}, rx={}, rz={}, filter={})",
            dimId, modeStr, rx, rz, eff.isEmpty() ? "<default>" : eff);
}
    // ** Attempts to replace the chunk generator in level internals **
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
    private static HolderSet<Biome> safeFromExistingSource(BiomeSource src, Registry<Biome> biomeReg) {
        try {
            var set = src.possibleBiomes();
            if (set != null && !set.isEmpty()) {
                return HolderSet.direct(new ArrayList<>(set));
            }
        } catch (Throwable ignored) {}

        return HolderSet.direct(biomeReg.stream().map(biomeReg::wrapAsHolder).toList());
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

    // ---------- BLACKLIST IDS + TAGS ----------
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

    // ---------- REMOVE "DECORATION-UNSAFE" BIOMES ----------
    // Vanilla assumes every biome has a features() list sized at least Decoration.values().length.
    final int expectedSteps = net.minecraft.world.level.levelgen.GenerationStep.Decoration.values().length;

    pool.removeIf(h -> {
        try {
            var feats = h.value().getGenerationSettings().features();
            return feats == null;
        } catch (Throwable t) {
            return false;
        }
    });

// ---------- EMPTY → ALL (then re-apply deny) ----------
if (pool.isEmpty()) {
    registry.stream().forEach(b -> pool.add(registry.wrapAsHolder(b)));

    if (!deny.isEmpty()) {
        pool.removeIf(h -> {
            var k = h.unwrapKey();
            return k.isPresent() && deny.contains(ChaosBiomeParsing.stableId(k.get()));
        });
    }
}

    return HolderSet.direct(new java.util.ArrayList<>(pool));
}
}
