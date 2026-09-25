package com.dermitio.chaoschunks.client.preset;

import com.dermitio.chaoschunks.client.config.ChaosChunksDefaultsConfig;
import com.dermitio.chaoschunks.data.world.ChaosChunksData;
import com.dermitio.chaoschunks.server.config.ChaosChunksPendingConfig;
import com.dermitio.chaoschunks.worldgen.chunk.ChaosGeneratorFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldDimensions;

/** A draft is shared between pages; only Done commits it to world creation. */
public final class ChaosChunksPresetScreen extends Screen {
    enum DimMode { ON, SAFE, OFF }

    static final class DimensionDraft {
        final String id;
        final boolean supportsRandomization;
        final BiomeSelection biomes;
        DimMode mode;
        long seedSalt;
        long terrainSalt;
        boolean showText;

        DimensionDraft(String id, boolean supportsRandomization, String text, DimMode mode, long seed, long terrain) {
            this.id = id;
            this.supportsRandomization = supportsRandomization;
            this.biomes = new BiomeSelection(text);
            this.mode = mode;
            this.seedSalt = supportsRandomization ? seed : 0;
            this.terrainSalt = supportsRandomization ? terrain : 0;
        }
    }

    record BiomeEntry(String id, boolean tag) {
        String label() { return (tag ? "#" : "") + id; }
    }

    private final CreateWorldScreen parent;
    final List<DimensionDraft> dimensions = new ArrayList<>();
    final List<BiomeEntry> biomeEntries = new ArrayList<>();
    final BiomeSelection global;
    String regionX;
    String regionZ;
    boolean showGlobalText;
    private PresetList list;
    private double scroll;

    public ChaosChunksPresetScreen(CreateWorldScreen parent) {
        this(parent, parent.getUiState().getSettings());
    }

    public ChaosChunksPresetScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.literal("ChaosChunks customization"));
        this.parent = parent;
        if (context == null) context = parent.getUiState().getSettings();
        var pending = ChaosChunksPendingConfig.peek();
        var server = Minecraft.getInstance().getSingleplayerServer();
        ChaosChunksData saved = server == null || server.overworld() == null ? null
                : ChaosChunksData.get(server.overworld().getDataStorage());
        regionX = String.valueOf(pending != null ? pending.regionX() : saved != null ? saved.regionX : 1);
        regionZ = String.valueOf(pending != null ? pending.regionZ() : saved != null ? saved.regionZ : 1);
        global = new BiomeSelection(pending != null ? pending.globalBiomes() : saved != null ? saved.globalBiomes : "");
        Map<String, String> texts = pending != null ? pending.dimensionBiomes() : saved != null ? saved.dimensionBiomes : Map.of();
        Map<String, String> modes = pending != null ? pending.dimensionModes() : saved != null ? saved.dimensionModes : Map.of();
        Map<String, Long> seeds = pending != null ? pending.dimensionSeedRandomizers() : saved != null ? saved.dimensionSeedRandomizers : Map.of();
        Map<String, Long> terrain = pending != null ? pending.dimensionTerrainRandomizers() : saved != null ? saved.dimensionTerrainRandomizers : Map.of();

        var selected = context.selectedDimensions().dimensions();
        var keys = new LinkedHashSet<>(selected.keySet());
        keys.addAll(context.datapackDimensions().registryKeySet());
        for (var key : WorldDimensions.keysInOrder(keys).toList()) {
            String id = key.identifier().toString();
            var stem = selected.get(key);
            if (stem == null) stem = context.datapackDimensions().getValue(key);
            boolean compatible = stem != null && ChaosGeneratorFactory.canWrap(stem.generator());
            DimMode mode;
            try {
                mode = DimMode.valueOf(modes.getOrDefault(id, modes.getOrDefault("*",
                        ChaosChunksDefaultsConfig.defaultDimensionMode().name())).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                mode = DimMode.ON;
            }
            long seed = initialSalt(seeds, id, !modes.containsKey(id) && ChaosChunksDefaultsConfig.regionSeedMode()
                    == ChaosChunksDefaultsConfig.RegionSeedMode.RANDOMIZED_REGION_SEED);
            long terrainSeed = ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()
                    ? initialSalt(terrain, id, !modes.containsKey(id) && ChaosChunksDefaultsConfig.terrainProfileMode()
                        == ChaosChunksDefaultsConfig.TerrainProfileMode.RANDOMIZED_TERRAIN_PROFILES) : 0;
            dimensions.add(new DimensionDraft(id, compatible, texts.getOrDefault(id, ""), mode, seed, terrainSeed));
        }
        var biomes = context.worldgenLoadContext().lookupOrThrow(Registries.BIOME);
        biomes.listElements().forEach(h -> biomeEntries.add(new BiomeEntry(h.key().identifier().toString(), false)));
        biomes.listTags().forEach(t -> biomeEntries.add(new BiomeEntry(t.key().location().toString(), true)));
        biomeEntries.sort(java.util.Comparator.comparing(BiomeEntry::label));
    }

    private static long initialSalt(Map<String, Long> values, String id, boolean defaultRandomized) {
        Long value = values.getOrDefault(id, values.get("*"));
        if (value != null) return value == ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS ? newSalt() : value;
        return defaultRandomized ? newSalt() : 0;
    }

    static long newSalt() {
        long salt;
        do { salt = ThreadLocalRandom.current().nextLong(); }
        while (salt == 0 || salt == ChaosChunksPendingConfig.RANDOMIZE_DEFAULT_DIMENSIONS);
        return salt;
    }

    @Override
    protected void init() {
        list = addRenderableWidget(new PresetList(width, 48, height - 36));
        addDimensionRow(null);
        dimensions.forEach(this::addDimensionRow);
        list.setScrollAmount(scroll);
        int buttonWidth = Math.min(150, (width - 36) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            applySettings();
            onClose();
        }).bounds(width / 2 - buttonWidth - 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, buttonWidth, 20).build());
    }

    private void addDimensionRow(DimensionDraft dimension) {
        String label = dimension == null ? "Global" : dimension.id;
        String summary = dimension == null ? "Default biome rules and region size"
                : dimension.mode + " / " + (dimension.biomes.isBlank() ? "Global biome rules" : "Custom biome rules");
        Button open = Button.builder(Component.literal(label), b -> {
            scroll = list.scrollAmount();
            minecraft.gui.setScreen(new ChaosChunksDimensionScreen(this, dimension));
        }).bounds(0, 0, 200, 20).build();
        open.setTooltip(Tooltip.create(Component.literal(label + "\n" + summary)));
        list.add(new PresetList.Row(open) {
            @Override
            public void extractContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, boolean hovered, float partialTick) {
                open.setPosition(getContentX(), getContentY());
                open.setWidth(getContentWidth());
                drawWidgets(gfx, mouseX, mouseY, partialTick);
                gfx.text(font, font.plainSubstrByWidth(summary, getContentWidth()), getContentX() + 3, getContentY() + 24, 0xFFB9C3CF, false);
            }
        }, 42);
    }

    private void applySettings() {
        Map<String, String> texts = new LinkedHashMap<>();
        Map<String, String> modes = new LinkedHashMap<>();
        Map<String, Long> seeds = new LinkedHashMap<>();
        Map<String, Long> terrain = new LinkedHashMap<>();
        for (DimensionDraft dimension : dimensions) {
            texts.put(dimension.id, dimension.biomes.text().trim());
            modes.put(dimension.id, dimension.mode.name());
            if (dimension.seedSalt != 0) seeds.put(dimension.id, dimension.seedSalt);
            if (dimension.terrainSalt != 0) terrain.put(dimension.id, dimension.terrainSalt);
        }
        int rx = regionSize(regionX);
        int rz = regionSize(regionZ);
        ChaosChunksPendingConfig.set(rx, rz, global.text().trim(), texts, modes, seeds, terrain);
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.overworld() != null) {
            var data = ChaosChunksData.get(server.overworld().getDataStorage());
            data.enabled = true;
            data.regionX = rx;
            data.regionZ = rz;
            data.globalBiomes = global.text().trim();
            data.dimensionBiomes.clear();
            data.dimensionBiomes.putAll(texts);
            data.dimensionModes.clear();
            data.dimensionModes.putAll(modes);
            data.dimensionSeedRandomizers.clear();
            data.dimensionSeedRandomizers.putAll(seeds);
            data.dimensionTerrainRandomizers.clear();
            data.dimensionTerrainRandomizers.putAll(terrain);
            data.setDirty();
        }
    }

    static int regionSize(String value) {
        try { return Math.clamp(Integer.parseInt(value), 1, 512); }
        catch (NumberFormatException e) { return 1; }
    }

    @Override
    public void resize(int width, int height) {
        if (list != null) scroll = list.scrollAmount();
        super.resize(width, height);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        gfx.centeredText(font, Component.literal("Select Global or a dimension to customize"), width / 2, 28, 0xFFB9C3CF);
    }
}
