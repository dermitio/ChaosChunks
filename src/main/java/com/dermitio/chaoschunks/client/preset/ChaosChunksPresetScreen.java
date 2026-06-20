package com.dermitio.chaoschunks.client.preset;

import com.dermitio.chaoschunks.client.config.ChaosChunksDefaultsConfig;
import com.dermitio.chaoschunks.worldgen.biome.ChaosBiomeSource;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosGeneratorFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import com.dermitio.chaoschunks.data.catalog.ChaosChunksCatalog;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import com.dermitio.chaoschunks.data.catalog.ChaosChunksCatalog;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import java.util.Collections;
import com.dermitio.chaoschunks.mixin.NoiseBasedChunkGeneratorAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.dermitio.chaoschunks.data.catalog.ChaosBiomeParsing;
// does anyone ever give a crap about what they import?
// like Imma import some nonsense on all the classes and leave them there soon enough...
// oh wait.

// =========
// World-creation UI for Chaos region size, global biomes, and per-dimension modes //
// =========
public class ChaosChunksPresetScreen extends Screen {

    // ** Tracks whether dimension toggle UI has been initialized **
    private boolean togglesInitialized = false;

    // ** Parent CreateWorldScreen reference **
    private final CreateWorldScreen parent;

    // ** Enum defining chaos generation mode per dimension **
    private enum DimMode { ON, SAFE, OFF }

    // ** Stores generation mode per dimension **
    private final Map<ResourceKey<LevelStem>, DimMode> dimensionMode = new LinkedHashMap<>();

    // ** Stores biome selection text per dimension **
    private final Map<ResourceKey<LevelStem>, String> dimensionBiomeText = new LinkedHashMap<>();

    // ** Stores per-dimension randomizer salts; 0 means disabled **
    private final Map<ResourceKey<LevelStem>, Long> dimensionSeedRandomizers = new LinkedHashMap<>();

    // ** Stores per-dimension terrain profile randomizer salts; 0 means disabled **
    private final Map<ResourceKey<LevelStem>, Long> dimensionTerrainRandomizers = new LinkedHashMap<>();

    // ** Stores all datapack-provided dimension keys **
    private final java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.dimension.LevelStem>> datapackStemKeys;

    // ** Mode buttons for visible dimensions **
    private final List<Button> dimensionButtons = new ArrayList<>();

    // ** Seed randomizer icon buttons for visible dimensions **
    private final List<Button> dimensionSeedButtons = new ArrayList<>();

    // ** Terrain profile randomizer buttons for visible dimensions **
    private final List<Button> dimensionTerrainButtons = new ArrayList<>();

    // ** Biome input boxes for visible dimensions **
    private final List<EditBox> dimensionBiomeBoxes = new ArrayList<>();

    // ** Keys currently visible in the dimension list **
    private final List<ResourceKey<LevelStem>> visibleKeys = new ArrayList<>();

    // ** Cached dimension key order **
    private List<ResourceKey<LevelStem>> lastDimensionKeys = List.of();
    // ** Cached UI states **
    private String uiRegionX = "1";
    private String uiRegionZ = "1";
    private String uiGlobalBiomes = "";

    private EditBox regionXBox;
    private EditBox regionZBox;
    private EditBox biomesBox;
    private Button doneButton;
    private Button cancelButton;
    private int dimMaxScroll = 0;
    private int dimScroll = 0;
    private static final int DIM_ROW_H = 32;

    private int dimVisibleRows = 5;
    private int dimListTopY = 0;
    private int dimListMaxHeight = 0;
    private int dimListLeftX = 0;
    private final int dimListWidth = 452;

    private static final int DIM_BTN_W = 80;
    private static final int DIM_SEED_BTN_W = 20;
    private static final int DIM_TERRAIN_BTN_W = 20;
    private static final int DIM_BOX_GAP = 6;
    private static final int DIM_LIST_Y_OFFSET = 96;

    // ** Constructs the preset screen with optional context **
    public ChaosChunksPresetScreen(CreateWorldScreen parent) {
        this(parent, null);
    }

    // ** Constructs the preset screen and records available datapack dimensions **
    public ChaosChunksPresetScreen(CreateWorldScreen parent, WorldCreationContext ctx) {
        super(Component.translatable("chaoschunks.preset.title"));
        this.parent = parent;
        this.datapackStemKeys = (ctx == null)
                ? Collections.emptySet()
                : new LinkedHashSet<>(ctx.datapackDimensions().registryKeySet());
    }

    // ** Cycles a dimension mode to its next state **
    private static DimMode nextMode(DimMode m) {
        return switch (m) {
            case ON -> DimMode.SAFE;
            case SAFE -> DimMode.OFF;
            case OFF -> DimMode.ON;
        };
    }

    private static DimMode configuredDefaultMode() {
        return switch (ChaosChunksDefaultsConfig.defaultDimensionMode()) {
            case ON -> DimMode.ON;
            case SAFE -> DimMode.SAFE;
            case OFF -> DimMode.OFF;
        };
    }

    // ** Returns a display label for a dimension mode **
    private static String modeSuffix(DimMode m) {
        return switch (m) {
            case ON -> "[ON]";
            case SAFE -> "[SAFE]";
            case OFF -> "[OFF]";
        };
    }

    private static long newSeedRandomizerSalt() {
        long salt = ThreadLocalRandom.current().nextLong();
        return salt == 0L ? 1L : salt;
    }

    private static long defaultSeedRandomizerSalt() {
        return switch (ChaosChunksDefaultsConfig.regionSeedMode()) {
            case WORLD_SEED -> 0L;
            case RANDOMIZED_REGION_SEED -> newSeedRandomizerSalt();
        };
    }

    private static long defaultTerrainRandomizerSalt() {
        if (!ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()) return 0L;
        return switch (ChaosChunksDefaultsConfig.terrainProfileMode()) {
            case NORMAL -> 0L;
            case RANDOMIZED_TERRAIN_PROFILES -> newSeedRandomizerSalt();
        };
    }

    // ** Region seed randomization requires a generator ChaosChunks can replace safely **
    private static boolean canUseRegionSeedRandomizer(WorldDimensions dims, ResourceKey<LevelStem> key) {
        LevelStem stem = dims.dimensions().get(key);
        return stem != null && ChaosGeneratorFactory.canWrap(stem.generator());
    } // fun fact: trying to keep shit compatable is pain.

    // ** Retrieves world seed from UI with fallback **
    private long getWorldSeed() {
        try {
            return parent.getUiState().getSettings().options().seed();
        } catch (Throwable t) {
            return String.valueOf(parent.getUiState()).hashCode();
        }
    }

    // ** Builds a new LevelStem using ChaosBiomeSource **
    private static LevelStem buildChaosStemWithSeed(LevelStem original, long seed, int rx, int rz, HolderSet<Biome> allowed) {
        var newBiomeSource = new ChaosBiomeSource(seed, rx, rz, allowed);
        return ChaosGeneratorFactory.replaceStemBiomeSource(original, newBiomeSource);
    }

    // ** Converts ResourceKey to compact string identifier **
    private static String keyId(ResourceKey<?> key) {
        String s = String.valueOf(key);

        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }

        if (s.startsWith("ResourceKey[") && s.endsWith("]")) {
            return s.substring("ResourceKey[".length(), s.length() - 1);
        }
        return s;
    }
    // =========
    // Builds the top-level preset controls and restores pending or saved UI state //
    // =========
@Override
protected void init() {
    togglesInitialized = false;
    lastDimensionKeys = List.of();
    dimScroll = 0;

    for (Button b : dimensionButtons) removeWidget(b);
    dimensionButtons.clear();

    for (Button b : dimensionSeedButtons) removeWidget(b);
    dimensionSeedButtons.clear();

    for (Button b : dimensionTerrainButtons) removeWidget(b);
    dimensionTerrainButtons.clear();

    for (EditBox b : dimensionBiomeBoxes) removeWidget(b);
    dimensionBiomeBoxes.clear();

    visibleKeys.clear();

    var pending = com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.peek();
    if (pending != null) {
        uiRegionX = String.valueOf(pending.regionX());
        uiRegionZ = String.valueOf(pending.regionZ());
        uiGlobalBiomes = (pending.globalBiomes() == null) ? "" : pending.globalBiomes();
    } else {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.overworld() != null) {
            var storage = server.overworld().getDataStorage();
            var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);
            uiRegionX = String.valueOf(data.regionX);
            uiRegionZ = String.valueOf(data.regionZ);
            uiGlobalBiomes = (data.globalBiomes == null) ? "" : data.globalBiomes;
        }
    }

    int cx = this.width / 2;
    int y = 48;

    regionXBox = new EditBox(font, cx - 100, y, 200, 20, Component.literal("Region X"));
    regionXBox.setValue(uiRegionX);
    addRenderableWidget(regionXBox);

    y += 38;
    regionZBox = new EditBox(font, cx - 100, y, 200, 20, Component.literal("Region Z"));
    regionZBox.setValue(uiRegionZ);
    addRenderableWidget(regionZBox);

    y += 38;
    biomesBox = new EditBox(font, cx - 210, y, 420, 20, Component.literal("Default biomes"));
    biomesBox.setValue(uiGlobalBiomes);
    biomesBox.setMaxLength(4096);
    addRenderableWidget(biomesBox);

    y += 36;

    doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
        persistVisibleBiomeEdits();
        applySettings();
        Minecraft.getInstance().setScreen(parent);
    }).bounds(cx - 102, y, 100, 20).build());

    cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                    b -> Minecraft.getInstance().setScreen(parent))
            .bounds(cx + 2, y, 100, 20).build());
}

    // ** Stores biome edits from visible dimension boxes into the backing maps **
    private void persistVisibleBiomeEdits() {
        for (int i = 0; i < dimensionBiomeBoxes.size() && i < visibleKeys.size(); i++) {
            var key = visibleKeys.get(i);
            dimensionBiomeText.put(key, dimensionBiomeBoxes.get(i).getValue().trim());
        }
    }
        // =========
        // Rebuilds visible dimension controls while preserving edits outside the viewport //
        // =========
private void syncDimensionToggles() {
    persistVisibleBiomeEdits();

    // Pending config is read once so scrolling does not overwrite in-progress text edits.
    var pending = com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.peek();
    final Map<String, String> pBiomes = (pending == null) ? savedDimensionBiomes() : pending.dimensionBiomes();
    final Map<String, String> pModes  = (pending == null) ? savedDimensionModes() : pending.dimensionModes();
    final Map<String, Long> pSeeds = (pending == null) ? savedSeedRandomizers() : pending.dimensionSeedRandomizers();
    final Map<String, Long> pTerrain = (pending == null) ? savedTerrainRandomizers() : pending.dimensionTerrainRandomizers();
    final boolean showTerrainControls = ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization();

    parent.getUiState().updateDimensions((access, dims) -> {
        List<ResourceKey<LevelStem>> keys = this.collectAllDimensionKeys(dims);

        // New datapack dimensions are initialized without overwriting existing edits.
        for (ResourceKey<LevelStem> key : keys) {
            String id = stableId(key);

            dimensionMode.computeIfAbsent(key, k -> {
                if (pModes != null) {
                    String ms = pModes.get(id);
                    if (ms == null) ms = pModes.get(com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID);
                    if (ms != null) {
                        try {
                            return DimMode.valueOf(ms.trim().toUpperCase(java.util.Locale.ROOT));
                        } catch (Throwable ignored) {}
                    }
                }
                return configuredDefaultMode();
            });

            dimensionBiomeText.computeIfAbsent(key, k -> {
                if (pBiomes != null) {
                    String bt = pBiomes.get(id);
                    if (bt != null) return bt;
                }
                return "[],[],[],[]";
            });

            dimensionSeedRandomizers.computeIfAbsent(key, k -> {
                if (pSeeds != null) {
                    Long salt = pSeeds.get(id);
                    if (salt == null) salt = pSeeds.get(com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID);
                    if (salt != null && salt == com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS) {
                        return defaultSeedRandomizerSalt();
                    }
                    if (salt != null) return salt;
                }
                return defaultSeedRandomizerSalt();
            });

            dimensionTerrainRandomizers.computeIfAbsent(key, k -> {
                if (!showTerrainControls) return 0L;
                if (pTerrain != null) {
                    Long salt = pTerrain.get(id);
                    if (salt == null) salt = pTerrain.get(com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.DEFAULT_DIMENSION_ID);
                    if (salt != null && salt == com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS) {
                        return newSeedRandomizerSalt();
                    }
                    if (salt != null) return salt;
                }
                return defaultTerrainRandomizerSalt();
            });
        }

        // Layout
        dimListTopY = biomesBox.getY() + DIM_LIST_Y_OFFSET;
        dimListLeftX = width / 2 - (dimListWidth / 2);

        int bottomPad = 28;
        int availablePx = Math.max(DIM_ROW_H, (this.height - bottomPad) - dimListTopY);
        dimVisibleRows = Math.max(1, Math.min(keys.size(), availablePx / DIM_ROW_H));

        dimListMaxHeight = dimVisibleRows * DIM_ROW_H;

        dimMaxScroll = Math.max(0, (keys.size() - dimVisibleRows) * DIM_ROW_H);
        dimScroll = Math.max(0, Math.min(dimScroll, dimMaxScroll));

        lastDimensionKeys = keys;

        // Rebuild widgets
        for (Button b : dimensionButtons) removeWidget(b);
        dimensionButtons.clear();

        for (Button b : dimensionSeedButtons) removeWidget(b);
        dimensionSeedButtons.clear();

        for (Button b : dimensionTerrainButtons) removeWidget(b);
        dimensionTerrainButtons.clear();

        for (EditBox b : dimensionBiomeBoxes) removeWidget(b);
        dimensionBiomeBoxes.clear();

        visibleKeys.clear();

        int y = dimListTopY - dimScroll;
        int viewTop = dimListTopY;
        int viewBottom = dimListTopY + dimListMaxHeight;

        for (ResourceKey<LevelStem> key : keys) {
            int rowTop = y;
            int rowBottom = y + 20;

            boolean visible = rowBottom > viewTop && rowTop < viewBottom;
            if (visible) {
                DimMode currentMode = dimensionMode.getOrDefault(key, configuredDefaultMode());

                Button modeBtn = Button.builder(
                                Component.literal(modeSuffix(currentMode)),
                                b -> {
                                    DimMode m = nextMode(dimensionMode.getOrDefault(key, configuredDefaultMode()));
                                    dimensionMode.put(key, m);
                                    b.setMessage(Component.literal(modeSuffix(m)));
                                }
                        )
                        .bounds(dimListLeftX, y, DIM_BTN_W, 20)
                        .build();
                addRenderableWidget(modeBtn);
                dimensionButtons.add(modeBtn);

                int seedX = dimListLeftX + DIM_BTN_W + DIM_BOX_GAP;
                boolean seedRandomizerCompatible = canUseRegionSeedRandomizer(dims, key);
                if (!seedRandomizerCompatible) {
                    dimensionSeedRandomizers.put(key, 0L);
                }

                SeedRandomizerButton seedBtn = new SeedRandomizerButton(
                        seedX,
                        y,
                        DIM_SEED_BTN_W,
                        20,
                        seedRandomizerCompatible && dimensionSeedRandomizers.getOrDefault(key, 0L) != 0L,
                        seedRandomizerCompatible,
                        b -> {
                            if (!seedRandomizerCompatible) return;
                            long current = dimensionSeedRandomizers.getOrDefault(key, 0L);
                            long next = (current == 0L) ? newSeedRandomizerSalt() : 0L;
                            dimensionSeedRandomizers.put(key, next);
                            if (b instanceof SeedRandomizerButton seedButton) {
                                seedButton.setRandomized(next != 0L);
                            }
                        }
                );
                if (!seedRandomizerCompatible) {
                    seedBtn.setTooltip(Tooltip.create(Component.literal(
                            "Seed randomizer unavailable: this dimension uses a generator ChaosChunks cannot replace safely."
                    )));
                }
                addRenderableWidget(seedBtn);
                dimensionSeedButtons.add(seedBtn);

                int terrainX = seedX + DIM_SEED_BTN_W + DIM_BOX_GAP;
                int boxX;
                int boxW;
                if (showTerrainControls) {
                    boolean terrainRandomizerCompatible = canUseRegionSeedRandomizer(dims, key);
                    if (!terrainRandomizerCompatible) {
                        dimensionTerrainRandomizers.put(key, 0L);
                    }

                    TerrainProfileButton terrainBtn = new TerrainProfileButton(
                            terrainX,
                            y,
                            DIM_TERRAIN_BTN_W,
                            20,
                            terrainRandomizerCompatible && dimensionTerrainRandomizers.getOrDefault(key, 0L) != 0L,
                            b -> {
                                if (!terrainRandomizerCompatible) return;
                                long current = dimensionTerrainRandomizers.getOrDefault(key, 0L);
                                long next = (current == 0L) ? newSeedRandomizerSalt() : 0L;
                                dimensionTerrainRandomizers.put(key, next);
                                if (b instanceof TerrainProfileButton terrainButton) {
                                    terrainButton.setRandomized(next != 0L);
                                }
                            }
                    );
                    terrainBtn.setTooltip(Tooltip.create(Component.literal(terrainRandomizerCompatible
                            ? "Region terrain profiles: ᪠ normal, ፠ randomized"
                            : "Terrain profiles unavailable: this dimension uses a generator ChaosChunks cannot replace safely."
                    )));
                    addRenderableWidget(terrainBtn);
                    dimensionTerrainButtons.add(terrainBtn);

                    boxX = terrainX + DIM_TERRAIN_BTN_W + DIM_BOX_GAP;
                    boxW = dimListWidth - DIM_BTN_W - DIM_SEED_BTN_W - DIM_TERRAIN_BTN_W - (DIM_BOX_GAP * 3);
                } else {
                    dimensionTerrainRandomizers.put(key, 0L);
                    boxX = terrainX;
                    boxW = dimListWidth - DIM_BTN_W - DIM_SEED_BTN_W - (DIM_BOX_GAP * 2);
                }

                EditBox box = new EditBox(font, boxX, y, boxW, 20, Component.literal(keyId(key)));
                box.setValue(dimensionBiomeText.getOrDefault(key, "[],[],[],[]"));
                box.setMaxLength(4096);
                addRenderableWidget(box);
                dimensionBiomeBoxes.add(box);

                visibleKeys.add(key);
            }

            y += DIM_ROW_H;
        }

        return dims;
    });
}

    // ** Builds the dimension toggle UI once before rendering **
    private void buildDimensionToggles() {
        if (togglesInitialized) return;
        togglesInitialized = true;
        syncDimensionToggles();
    }

    // ** Builds SAFE biome set using dimension-specific biome sources when possible **
    @SuppressWarnings("unchecked")
    private static HolderSet<Biome> safeAllowedFromStem(
            ResourceKey<LevelStem> stemKey,
            LevelStem stem,
            HolderLookup.RegistryLookup<Biome> biomeLookup
    ) {
        if (stemKey.equals(LevelStem.NETHER)) return biomeLookup.getOrThrow(BiomeTags.IS_NETHER);
        if (stemKey.equals(LevelStem.END)) return biomeLookup.getOrThrow(BiomeTags.IS_END);
        if (stemKey.equals(LevelStem.OVERWORLD)) return biomeLookup.getOrThrow(BiomeTags.IS_OVERWORLD);

        try {
            Object gen = stem.generator();

            Object biomeSource = null;
            for (String name : new String[]{"biomeSource", "getBiomeSource"}) {
                try {
                    var m = gen.getClass().getMethod(name);
                    biomeSource = m.invoke(gen);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }

            if (biomeSource != null) {
                for (String name : new String[]{"possibleBiomes", "getPossibleBiomes"}) {
                    try {
                        var m = biomeSource.getClass().getMethod(name);
                        Object res = m.invoke(biomeSource);

                        if (res instanceof Iterable<?> it) {
                            List<Holder<Biome>> list = new ArrayList<>();
                            for (Object o : it) list.add((Holder<Biome>) o);
                            if (!list.isEmpty()) return HolderSet.direct(list);
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<Holder<Biome>> all = biomeLookup.listElements().map(h -> (Holder<Biome>) h).toList();
        return HolderSet.direct(all);
    }
        // ** Determines the default allowed biome set for a dimension based on mode and user input **
    private static HolderSet<Biome> defaultAllowedFor(
        ResourceKey<LevelStem> stemKey,
        HolderLookup.RegistryLookup<Biome> biomeLookup,
        boolean userSpecified,
        String biomesText,
        DimMode mode
) {
    if (userSpecified) return parseBiomeSelection(biomeLookup, biomesText);

    if (mode == DimMode.SAFE) {
        if (stemKey.equals(LevelStem.NETHER)) return biomeLookup.getOrThrow(BiomeTags.IS_NETHER);
        if (stemKey.equals(LevelStem.END)) return biomeLookup.getOrThrow(BiomeTags.IS_END);
        if (stemKey.equals(LevelStem.OVERWORLD)) return biomeLookup.getOrThrow(BiomeTags.IS_OVERWORLD);
    }

    List<Holder<Biome>> all = biomeLookup.listElements().map(h -> (Holder<Biome>) h).toList();
    return HolderSet.direct(all);
}

    // ** Collects every dimension key available from UI, datapacks, and cached catalog **
@SuppressWarnings("unchecked")
private List<ResourceKey<LevelStem>> collectAllDimensionKeys(WorldDimensions dims) {
    Set<ResourceKey<LevelStem>> allKeys = new LinkedHashSet<>();

    allKeys.addAll(dims.dimensions().keySet());
    allKeys.addAll(this.datapackStemKeys);
    allKeys.addAll(ChaosChunksCatalog.readDimKeys());

    return WorldDimensions.keysInOrder(allKeys).toList();
}

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

private static Map<String, Long> savedSeedRandomizers() {
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server == null || server.overworld() == null) return null;

    var storage = server.overworld().getDataStorage();
    var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);
    return data.dimensionSeedRandomizers;
}

private static Map<String, Long> savedTerrainRandomizers() {
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server == null || server.overworld() == null) return null;

    var storage = server.overworld().getDataStorage();
    var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);
    return data.dimensionTerrainRandomizers;
}

private static Map<String, String> savedDimensionBiomes() {
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server == null || server.overworld() == null) return null;

    var storage = server.overworld().getDataStorage();
    var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);
    return data.dimensionBiomes;
}

private static Map<String, String> savedDimensionModes() {
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server == null || server.overworld() == null) return null;

    var storage = server.overworld().getDataStorage();
    var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);
    return data.dimensionModes;
}

    // =========
    // Converts UI fields into pending config and saved world data //
    // =========
private void applySettings() {
    System.out.println("[ChaosChunks] applySettings() called");
    int rx = parseClamped(regionXBox.getValue(), 1, 512, 1);
    int rz = parseClamped(regionZBox.getValue(), 1, 512, 1);

    long worldSeed = getWorldSeed();
    String globalText = (biomesBox == null) ? "" : biomesBox.getValue().trim();

    java.util.Map<String, String> biomesByDim = new java.util.HashMap<>();
    java.util.Map<String, String> modesByDim = new java.util.HashMap<>();
    java.util.Map<String, Long> seedRandomizersByDim = new java.util.HashMap<>();
    java.util.Map<String, Long> terrainRandomizersByDim = new java.util.HashMap<>();

    dimensionBiomeText.forEach((k, v) -> biomesByDim.put(stableId(k), (v == null) ? "" : v.trim()));
    dimensionMode.forEach((k, v) -> modesByDim.put(stableId(k), (v == null) ? "ON" : v.name()));
    dimensionSeedRandomizers.forEach((k, v) -> {
        long salt = (v == null) ? 0L : v;
        if (salt != 0L) seedRandomizersByDim.put(stableId(k), salt);
    });
    if (ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()) {
        dimensionTerrainRandomizers.forEach((k, v) -> {
            long salt = (v == null) ? 0L : v;
            if (salt != 0L) terrainRandomizersByDim.put(stableId(k), salt);
        });
    }

    com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig.set(rx, rz, globalText, biomesByDim, modesByDim, seedRandomizersByDim, terrainRandomizersByDim);

    uiRegionX = String.valueOf(rx);
    uiRegionZ = String.valueOf(rz);
    uiGlobalBiomes = globalText;

    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server != null) {
        var storage = server.overworld().getDataStorage();
        var data = com.dermitio.chaoschunks.data.world.ChaosChunksData.get(storage);

        data.enabled = true;

        data.regionX = rx;
        data.regionZ = rz;
        data.globalBiomes = globalText;

        data.dimensionBiomes.clear();
        data.dimensionModes.clear();
        data.dimensionSeedRandomizers.clear();
        data.dimensionTerrainRandomizers.clear();
        data.dimensionBiomes.putAll(biomesByDim);
        data.dimensionModes.putAll(modesByDim);
        data.dimensionSeedRandomizers.putAll(seedRandomizersByDim);
        data.dimensionTerrainRandomizers.putAll(terrainRandomizersByDim);

        data.setDirty();
    }
}

    // ** Parses an integer and clamps it to a range with fallback **
private static int parseClamped(String s, int min, int max, int fallback) {
    try {
        int v = Integer.parseInt(s.trim());
        return Math.max(min, Math.min(max, v));
    } catch (Exception e) {
        return fallback;
    }
}
    // =========
    // Parses include and blacklist biome expressions into a concrete HolderSet //
    // =========
@SuppressWarnings("unchecked")
private static HolderSet<Biome> parseBiomeSelection(HolderLookup.RegistryLookup<Biome> biomeLookup, String text) {
    var spec = ChaosBiomeParsing.parse(text);

    java.util.LinkedHashSet<Holder<Biome>> pool = new java.util.LinkedHashSet<>();

    // ---------- INCLUDES ----------
    if (!spec.hasAnyIncludes()) {
        biomeLookup.listElements().forEach(h -> pool.add((Holder<Biome>) h));
    } else {

        // include tags
        for (String tagStr : spec.includeTagIds()) {
            Identifier tagId = Identifier.tryParse(tagStr);
            if (tagId == null) continue;
            try {
                HolderSet<Biome> set = biomeLookup.getOrThrow(TagKey.create(Registries.BIOME, tagId));
                set.forEach(h -> pool.add((Holder<Biome>) h));
            } catch (Throwable ignored) {}
        }

        // include ids
        for (String idStr : spec.includeIds()) {
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
            biomeLookup.get(key).ifPresent(h -> pool.add((Holder<Biome>) h));
        }
    }

    // ---------- BLACKLIST IDS + TAGS ----------
    java.util.HashSet<String> deny = new java.util.HashSet<>(spec.blacklistIds());

    for (String tagStr : spec.blacklistTagIds()) {
        Identifier tagId = Identifier.tryParse(tagStr);
        if (tagId == null) continue;
        try {
            HolderSet<Biome> set = biomeLookup.getOrThrow(TagKey.create(Registries.BIOME, tagId));
            set.forEach(h -> {
                var k = ((Holder<Biome>) h).unwrapKey();
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

    // ---------- EMPTY → ALL ----------
    if (pool.isEmpty()) {
        biomeLookup.listElements().forEach(h -> pool.add((Holder<Biome>) h));
    }

    return HolderSet.direct(new java.util.ArrayList<>(pool));
}

    // ** Indicates this screen does not pause the game **
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ** Handles mouse wheel scrolling for the dimension list **
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
    buildDimensionToggles();
        int top = dimListTopY;
        int bottom = dimListTopY + dimListMaxHeight;
        int left = dimListLeftX;
        int right = dimListLeftX + dimListWidth;

        boolean overList = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
if (overList) {
if (dimMaxScroll > 0 && deltaY != 0) {
    int dir = (deltaY < 0) ? 1 : -1;

    dimScroll = Math.max(0, Math.min(dimScroll + dir * DIM_ROW_H, dimMaxScroll));
    syncDimensionToggles();
    return true;
}
}

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    // ** Renders UI elements and labels for the preset screen **
// has been 26.1'd
@Override
public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    buildDimensionToggles();

    this.extractTransparentBackground(gfx);
    super.extractRenderState(gfx, mouseX, mouseY, partialTick);

    int cx = this.width / 2;

    gfx.centeredText(this.font,
            Component.translatable("chaoschunks.preset.region_x"),
            cx, regionXBox.getY() - 12, 0xFFFFFFFF);

    gfx.centeredText(this.font,
            Component.translatable("chaoschunks.preset.region_z"),
            cx, regionZBox.getY() - 12, 0xFFFFFFFF);

    gfx.centeredText(this.font,
            Component.literal("Default biomes"),
            cx, biomesBox.getY() - 12, 0xFFFFFFFF);

    for (int i = 0; i < visibleKeys.size() && i < dimensionBiomeBoxes.size(); i++) {
        ResourceKey<LevelStem> key = visibleKeys.get(i);
        EditBox box = dimensionBiomeBoxes.get(i);

        String label = keyId(key);
        int labelX = box.getX();
        int labelY = box.getY() - 10;

        gfx.text(this.font, Component.literal(label), labelX, labelY, 0xFFFFFFFF, false);
    }

    gfx.text(this.font, Component.literal("Mode"), dimListLeftX, dimListTopY - 12, 0xFFFFFFFF, false);
    gfx.text(this.font, Component.literal("Seed"),
            dimListLeftX + DIM_BTN_W + DIM_BOX_GAP, dimListTopY - 12, 0xFFFFFFFF, false);
    if (ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()) {
        gfx.text(this.font, Component.literal("Type"),
                dimListLeftX + DIM_BTN_W + DIM_SEED_BTN_W + (DIM_BOX_GAP * 2), dimListTopY - 12, 0xFFFFFFFF, false);
        gfx.text(this.font, Component.literal("Biomes (blank = default)"),
                dimListLeftX + DIM_BTN_W + DIM_SEED_BTN_W + DIM_TERRAIN_BTN_W + (DIM_BOX_GAP * 3), dimListTopY - 22, 0xFFFFFFFF, false);
    } else {
        gfx.text(this.font, Component.literal("Biomes (blank = default)"),
                dimListLeftX + DIM_BTN_W + DIM_SEED_BTN_W + (DIM_BOX_GAP * 2), dimListTopY - 22, 0xFFFFFFFF, false);
    }
}

private static final class TerrainProfileButton extends Button {

    private boolean randomized;

    private TerrainProfileButton(int x, int y, int width, int height, boolean randomized, Button.OnPress onPress) {
        super(x, y, width, height, Component.literal(symbol(randomized)), onPress, DEFAULT_NARRATION);
        this.randomized = randomized;
    }

    private void setRandomized(boolean randomized) {
        this.randomized = randomized;
        this.setMessage(Component.literal(symbol(randomized)));
    }

    private static String symbol(boolean randomized) {
        return randomized ? "፠" : "᪠";
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractDefaultSprite(graphics);
        graphics.centeredText(
                Minecraft.getInstance().font,
                Component.literal(symbol(randomized)),
                this.getX() + this.getWidth() / 2,
                this.getY() + 6,
                randomized ? 0xFFFFD36A : 0xFFFFFFFF
        );
    }
}

private static final class SeedRandomizerButton extends Button {

	private static final int ICON_SIZE = 10;
	private static final int ICON_OFFSET_X = 6;
	private static final int ICON_OFFSET_Y = 5;

	private boolean randomized;
    private final boolean compatible;

    private SeedRandomizerButton(int x, int y, int width, int height, boolean randomized, boolean compatible, Button.OnPress onPress) {
        super(x, y, width, height, Component.literal("Seed randomizer"), onPress, DEFAULT_NARRATION);
        this.randomized = compatible && randomized;
        this.compatible = compatible;
        this.active = compatible;
    }

    private void setRandomized(boolean randomized) {
        this.randomized = compatible && randomized;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractDefaultSprite(graphics);

        int x0 = this.getX() + ICON_OFFSET_X;
        int y0 = this.getY() + ICON_OFFSET_Y;

        if (!compatible) {
            graphics.fill(x0, y0, x0 + ICON_SIZE, y0 + ICON_SIZE, 0xFFFF3333);
            return;
        }

        if (!randomized) {
            graphics.fill(x0, y0, x0 + ICON_SIZE, y0 + ICON_SIZE, 0xFFFFFFFF);
            return;
        }

        SeedRandomizerButtonTheme.Theme theme = SeedRandomizerButtonTheme.activeTheme();
        int[] colors = theme.colors();
        int offset = (int) ((System.currentTimeMillis() / theme.frameMs()) % colors.length);

        for (int i = 0; i < colors.length; i++) {
            int sx0 = x0 + (i * ICON_SIZE / colors.length);
            int sx1 = x0 + ((i + 1) * ICON_SIZE / colors.length);
            graphics.fill(sx0, y0, sx1, y0 + ICON_SIZE, colors[(i + offset) % colors.length]);
        }
    }
}
}
