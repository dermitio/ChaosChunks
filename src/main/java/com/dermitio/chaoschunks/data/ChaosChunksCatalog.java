package com.dermitio.chaoschunks.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.function.Predicate;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class ChaosChunksCatalog {

    // ** Stores serialized catalog data for dimensions, biome tags, and biome IDs **
    public record Catalog(List<String> dims, List<String> biomeTags, List<String> biomeIds) {}

    // ** Creates the JSON serializer used for catalog persistence **
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ** Prevents instantiation since this class only provides static catalog utilities **
    private ChaosChunksCatalog() {}

    // ** Resolves the filesystem location where the catalog JSON is stored **
    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("chaoschunks").resolve("catalog.json");
    }

    // ** Produces stable string IDs from registry keys or tag keys **
    private static String stableId(Object keyLike) {
        String s = String.valueOf(keyLike);
        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }
        return s;
    }

    // ** Builds and writes a catalog by scanning datapack resources **
    public static void writeFromResources(ResourceManager rm) {
        try {
            Path path = filePath();
            Files.createDirectories(path.getParent());

            List<String> dimIds = rm.listResources("dimension",
                            id -> id.getPath().endsWith(".json"))
                    .keySet().stream()
                    .map(id -> {
                        String p = id.getPath();
                        String prefix = "dimension/";
                        if (!p.startsWith(prefix)) return null;
                        String name = p.substring(prefix.length(), p.length() - ".json".length());
                        return id.getNamespace() + ":" + name;
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            List<String> biomeIds = rm.listResources("worldgen/biome",
                            id -> id.getPath().endsWith(".json"))
                    .keySet().stream()
                    .map(id -> {
                        String p = id.getPath();
                        String prefix = "worldgen/biome/";
                        if (!p.startsWith(prefix)) return null;
                        String name = p.substring(prefix.length(), p.length() - ".json".length());
                        return id.getNamespace() + ":" + name;
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            List<String> biomeTags = rm.listResources("tags/worldgen/biome",
                            id -> id.getPath().endsWith(".json"))
                    .keySet().stream()
                    .map(id -> {
                        String p = id.getPath();
                        String prefix = "tags/worldgen/biome/";
                        if (!p.startsWith(prefix)) return null;
                        String name = p.substring(prefix.length(), p.length() - ".json".length());
                        return "#" + id.getNamespace() + ":" + name;
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            if (dimIds.isEmpty() && biomeIds.isEmpty() && biomeTags.isEmpty()) return;

            Catalog cat = new Catalog(dimIds, biomeTags, biomeIds);

            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(cat, w);
            }
        } catch (Throwable ignored) {
        }
    }

    // ** Scans JSON resources using reflection to remain compatible across mapping changes **
    @SuppressWarnings("unchecked")
    private static List<String> scanJsonIds(ResourceManager rm, String root, boolean isTag) {
        try {
            Method listResources = null;
            for (Method m : rm.getClass().getMethods()) {
                if (m.getName().equals("listResources") && m.getParameterCount() == 2) {
                    listResources = m;
                    break;
                }
            }
            if (listResources == null) return List.of();

            Predicate<Object> pred = (obj) -> {
                String p = getPath(obj);
                return p != null && p.endsWith(".json");
            };

            Object mapObj = listResources.invoke(rm, root, pred);
            if (!(mapObj instanceof Map<?, ?> map)) return List.of();

            List<String> out = new ArrayList<>();
            String prefix = root.endsWith("/") ? root : (root + "/");

            for (Object keyObj : map.keySet()) {
                String ns = getNamespace(keyObj);
                String p = getPath(keyObj);
                if (ns == null || p == null) continue;
                if (!p.startsWith(prefix) || !p.endsWith(".json")) continue;

                String stripped = p.substring(prefix.length(), p.length() - ".json".length());
                if (stripped.isEmpty()) continue;

                String id = ns + ":" + stripped;
                out.add(isTag ? ("#" + id) : id);
            }

            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ** Extracts namespace via reflection from resource identifiers **
    private static String getNamespace(Object idObj) {
        try {
            Method m = idObj.getClass().getMethod("getNamespace");
            Object v = m.invoke(idObj);
            return (v instanceof String s) ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ** Extracts path via reflection from resource identifiers **
    private static String getPath(Object idObj) {
        try {
            Method m = idObj.getClass().getMethod("getPath");
            Object v = m.invoke(idObj);
            return (v instanceof String s) ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ** Builds and writes the catalog using the live server registries **
    public static void writeFromServer(MinecraftServer server) {
        try {
            Path path = filePath();
            Files.createDirectories(path.getParent());

            Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
            Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);

            List<String> dimIds = stems.keySet().stream()
                    .map(ChaosChunksCatalog::stableId)
                    .distinct()
                    .sorted()
                    .toList();

            List<String> biomeIds = biomes.keySet().stream()
                    .map(ChaosChunksCatalog::stableId)
                    .distinct()
                    .sorted()
                    .toList();

            List<String> biomeTags = collectRegistryTagsAsStrings(biomes).stream()
                    .map(t -> t.startsWith("#") ? t : ("#" + t))
                    .distinct()
                    .sorted()
                    .toList();

            Catalog cat = new Catalog(dimIds, biomeTags, biomeIds);

            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(cat, w);
            }
        } catch (Throwable ignored) {
        }
    }

    // ** Reads the catalog JSON from disk and ensures null-safe lists **
    public static Catalog read() {
        Path path = filePath();
        if (!Files.exists(path)) return new Catalog(List.of(), List.of(), List.of());

        try (Reader r = Files.newBufferedReader(path)) {
            Catalog c = GSON.fromJson(r, Catalog.class);
            if (c == null) return new Catalog(List.of(), List.of(), List.of());

            return new Catalog(
                    (c.dims() == null) ? List.of() : c.dims(),
                    (c.biomeTags() == null) ? List.of() : c.biomeTags(),
                    (c.biomeIds() == null) ? List.of() : c.biomeIds()
            );
        } catch (IOException | JsonSyntaxException e) {
            return new Catalog(List.of(), List.of(), List.of());
        }
    }

    // ** Converts stored dimension ID strings into registry keys **
    public static List<ResourceKey<LevelStem>> readDimKeys() {
        Catalog c = read();
        List<ResourceKey<LevelStem>> out = new ArrayList<>();
        for (String s : c.dims()) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) out.add(ResourceKey.create(Registries.LEVEL_STEM, id));
        }
        return out;
    }

    // ** Collects registry tags via reflection to avoid mapping breakage **
    @SuppressWarnings("unchecked")
    private static List<String> collectRegistryTagsAsStrings(Object registry) {
        try {
            Method getTags = registry.getClass().getMethod("getTags");
            Object res = getTags.invoke(registry);

            Iterable<?> it = null;

            if (res instanceof Stream<?> stream) it = stream.toList();
            else if (res instanceof Iterable<?> iterable) it = iterable;

            if (it == null) return List.of();

            List<String> tags = new ArrayList<>();
            for (Object elem : it) {
                Object tagKey = extractTagKey(elem);
                if (tagKey != null) tags.add(stableId(tagKey));
            }
            return tags;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    // ** Extracts tag keys from registry tag entries using reflection-safe probing **
    private static Object extractTagKey(Object elem) {
        if (elem == null) return null;
        if (elem instanceof Map.Entry<?, ?> e) return e.getKey();

        for (String m : new String[]{"getFirst","getLeft","first","left","key","getKey"}) {
            try {
                Method mm = elem.getClass().getMethod(m);
                return mm.invoke(elem);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
