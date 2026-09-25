package com.dermitio.chaoschunks.client.preset;

import com.dermitio.chaoschunks.client.config.ChaosChunksDefaultsConfig;
import com.dermitio.chaoschunks.data.catalog.ChaosBiomeParsing;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Biome rules and generation settings for one dimension, or the shared Global defaults. */
final class ChaosChunksDimensionScreen extends Screen {
    private static final int RULE_WIDTH = 60;
    private static final int RULE_GAP = 4;
    private final ChaosChunksPresetScreen parent;
    private final ChaosChunksPresetScreen.DimensionDraft dimension;
    private boolean settingsTab;
    private String searchText = "";
    private PresetList list;
    private EditBox rawText;
    private boolean syncingText;
    private double biomeScroll;
    private double settingsScroll;
    private int matches;

    ChaosChunksDimensionScreen(ChaosChunksPresetScreen parent, ChaosChunksPresetScreen.DimensionDraft dimension) {
        super(Component.literal(dimension == null ? "Global" : dimension.id));
        this.parent = parent;
        this.dimension = dimension;
    }

    private BiomeSelection selection() { return dimension == null ? parent.global : dimension.biomes; }
    private boolean inherited() { return dimension != null && selection().isBlank(); }
    private BiomeSelection effectiveSelection() { return inherited() ? parent.global : selection(); }
    private boolean showText() { return dimension == null ? parent.showGlobalText : dimension.showText; }

    private void rememberScroll() {
        if (list == null) return;
        if (settingsTab) settingsScroll = list.scrollAmount();
        else biomeScroll = list.scrollAmount();
    }

    private void changeTab(boolean settings) {
        rememberScroll();
        settingsTab = settings;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rawText = null;
        int pageWidth = Math.min(760, width - 28);
        int left = (width - pageWidth) / 2;
        int tabWidth = (pageWidth - 4) / 2;
        Button biomes = addRenderableWidget(Button.builder(Component.literal("Biomes"), b -> changeTab(false))
                .bounds(left, 38, tabWidth, 20).build());
        biomes.active = settingsTab;
        Button settings = addRenderableWidget(Button.builder(Component.literal("Settings"), b -> changeTab(true))
                .bounds(left + tabWidth + 4, 38, tabWidth, 20).build());
        settings.active = !settingsTab;
        if (settingsTab) buildSettings();
        else buildBiomes(left, pageWidth);
        addRenderableWidget(Button.builder(Component.literal("Back to dimensions"), b -> onClose())
                .bounds(width / 2 - Math.min(100, (width - 28) / 2), height - 28, Math.min(200, width - 28), 20).build());
    }

    private void buildBiomes(int left, int pageWidth) {
        EditBox search = new EditBox(font, left, 65, pageWidth - 112, 20, Component.literal("Search biome IDs and tags"));
        search.setMaxLength(256);
        search.setHint(Component.literal("Search IDs / #tags"));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            biomeScroll = 0;
            rebuildBiomeRows(false);
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal(showText() ? "Hide text" : "Show text"), b -> {
            rememberScroll();
            if (dimension == null) parent.showGlobalText = !parent.showGlobalText;
            else dimension.showText = !dimension.showText;
            rebuildWidgets();
        }).bounds(left + pageWidth - 106, 65, 106, 20)
                .tooltip(Tooltip.create(Component.literal("Show or hide the editable whitelist / blacklist expression."))).build());
        list = addRenderableWidget(new PresetList(width, 104, height - (showText() ? 80 : 36)));
        rebuildBiomeRows(false);
        list.setScrollAmount(biomeScroll);
        if (showText()) {
            rawText = new EditBox(font, left, height - 60, pageWidth, 20, Component.literal("Editable biome whitelist and blacklist"));
            rawText.setMaxLength(1_000_000);
            rawText.setValue(selection().text());
            rawText.setHint(Component.literal(dimension == null ? "Blank = mode defaults" : "Blank = inherit Global"));
            rawText.setTooltip(Tooltip.create(Component.literal(
                    "[whitelist tags],[whitelist IDs],[blacklist IDs],[blacklist tags]\n"
                    + "Use commas within each group. Prefix tags with #. Blacklists take priority.\n"
                    + (dimension == null ? "Blank uses each dimension's mode defaults." : "Blank inherits Global; [],[],[],[] uses this dimension's mode defaults."))));
            rawText.setResponder(value -> {
                if (syncingText) return;
                selection().setText(value);
                rebuildBiomeRows(true);
            });
            addRenderableWidget(rawText);
        }
    }

    private void rebuildBiomeRows(boolean preserveScroll) {
        if (list == null || settingsTab) return;
        double scroll = preserveScroll ? list.scrollAmount() : 0;
        list.reset();
        var entries = new LinkedHashSet<>(parent.biomeEntries);
        var spec = ChaosBiomeParsing.parse(effectiveSelection().text());
        spec.includeTagIds().forEach(id -> entries.add(new ChaosChunksPresetScreen.BiomeEntry(id, true)));
        spec.blacklistTagIds().forEach(id -> entries.add(new ChaosChunksPresetScreen.BiomeEntry(id, true)));
        spec.includeIds().forEach(id -> entries.add(new ChaosChunksPresetScreen.BiomeEntry(id, false)));
        spec.blacklistIds().forEach(id -> entries.add(new ChaosChunksPresetScreen.BiomeEntry(id, false)));
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        matches = 0;
        for (var entry : entries.stream().sorted(Comparator.comparing(ChaosChunksPresetScreen.BiomeEntry::label)).toList()) {
            if (!entry.label().toLowerCase(Locale.ROOT).contains(query)) continue;
            list.add(new BiomeRow(entry), 28);
            matches++;
        }
        list.setScrollAmount(scroll);
    }

    private void toggleRule(ChaosChunksPresetScreen.BiomeEntry entry, boolean blacklist) {
        if (inherited()) selection().setText(parent.global.text());
        selection().toggle(entry.id(), entry.tag(), blacklist);
        if (rawText != null) {
            syncingText = true;
            rawText.setValue(selection().text());
            syncingText = false;
        }
    }

    private final class BiomeRow extends PresetList.Row {
        private final ChaosChunksPresetScreen.BiomeEntry entry;
        private final Button whitelist;
        private final Button blacklist;
        private final boolean available;

        BiomeRow(ChaosChunksPresetScreen.BiomeEntry entry) {
            super(ruleButton(entry, false), ruleButton(entry, true));
            this.entry = entry;
            this.whitelist = (Button) widgets.get(0);
            this.blacklist = (Button) widgets.get(1);
            this.available = parent.biomeEntries.contains(entry);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int right = getContentRight();
            int whitelistX = right - RULE_WIDTH * 2 - RULE_GAP;
            whitelist.setPosition(whitelistX, getContentY());
            blacklist.setPosition(right - RULE_WIDTH, getContentY());
            updateRuleButton(whitelist, entry, false);
            updateRuleButton(blacklist, entry, true);
            String label = entry.label();
            int labelWidth = whitelistX - getContentX() - 6;
            gfx.text(font, ellipsize(label, labelWidth), getContentX(), getContentY() + 6,
                    available ? entry.tag() ? 0xFF9DD8FF : 0xFFE2E8F0 : 0xFFFFC078, false);
            if (hovered && mouseX < whitelistX) {
                gfx.setTooltipForNextFrame(Component.literal(label + (available ? "" : "\nNot in the loaded datapacks; kept from your text.")), mouseX, mouseY);
            }
            drawWidgets(gfx, mouseX, mouseY, partialTick);
        }
    }

    private Button ruleButton(ChaosChunksPresetScreen.BiomeEntry entry, boolean blacklist) {
        String name = blacklist ? "Blacklist" : "Whitelist";
        return Button.builder(Component.literal("[ ]"), b -> toggleRule(entry, blacklist))
                .bounds(0, 0, RULE_WIDTH, 20)
                .tooltip(Tooltip.create(Component.literal(name + " " + entry.label() + "\nClick again to remove this rule.")))
                .createNarration(ignored -> Component.literal(name + " " + entry.label() + ": "
                        + (effectiveSelection().contains(entry.id(), entry.tag(), blacklist) ? "selected" : "not selected")))
                .build();
    }

    private void updateRuleButton(Button button, ChaosChunksPresetScreen.BiomeEntry entry, boolean blacklist) {
        boolean selected = effectiveSelection().contains(entry.id(), entry.tag(), blacklist);
        button.setMessage(Component.literal(selected ? "[x]" : "[ ]").withColor(
                selected ? blacklist ? 0xFF9999 : 0x91E6A5 : 0xB9C3CF));
    }

    private void buildSettings() {
        list = addRenderableWidget(new PresetList(width, 66, height - 36));
        if (dimension == null) {
            addRegionSetting("Region size X (chunks, 1-512)", parent.regionX, value -> parent.regionX = value);
            addRegionSetting("Region size Z (chunks, 1-512)", parent.regionZ, value -> parent.regionZ = value);
            Button reset = button("Reset Global biome rules", b -> parent.global.setText(""));
            reset.setTooltip(Tooltip.create(Component.literal("Clear Global whitelists and blacklists. Dimensions with custom rules keep them.")));
            addSetting("Biome defaults", reset);
        } else {
            Button inherit = button(inheritanceLabel(), b -> {
                if (inherited()) selection().setText(parent.global.isBlank() ? "[],[],[],[]" : parent.global.text());
                else selection().setText("");
                b.setMessage(Component.literal(inheritanceLabel()));
            });
            inherit.setTooltip(Tooltip.create(Component.literal("Global rules follow edits to Global. Custom rules override Global for this dimension.")));
            addSetting("Biome rules", inherit);
            Button mode = button("[" + dimension.mode + "]", b -> {
                var modes = ChaosChunksPresetScreen.DimMode.values();
                dimension.mode = modes[(dimension.mode.ordinal() + 1) % modes.length];
                b.setMessage(Component.literal("[" + dimension.mode + "]"));
            });
            mode.setTooltip(Tooltip.create(Component.literal("ON: whitelist rules narrow the biome pool. SAFE: start with dimension-native biomes and add whitelisted biomes. Blacklists remove biomes in both modes. OFF: keep the original generator.")));
            addSetting("Chaos generation", mode, 80, null);
            Button seed = new SeedRandomizerButton(dimension.seedSalt != 0, dimension.supportsRandomization, b -> {
                dimension.seedSalt = dimension.seedSalt == 0 ? ChaosChunksPresetScreen.newSalt() : 0;
                ((SeedRandomizerButton) b).setRandomized(dimension.seedSalt != 0);
            });
            seed.active = dimension.supportsRandomization;
            seed.setTooltip(Tooltip.create(Component.literal(seed.active
                    ? "Choose whether every region uses the world seed or its own randomized seed."
                    : "Unavailable for this dimension's generator.")));
            addSetting("Region seed", seed, 20, this::seedLabel);
            Button terrain = new TerrainProfileButton(dimension.terrainSalt != 0, b -> {
                dimension.terrainSalt = dimension.terrainSalt == 0 ? ChaosChunksPresetScreen.newSalt() : 0;
                ((TerrainProfileButton) b).setRandomized(dimension.terrainSalt != 0);
            });
            terrain.active = dimension.supportsRandomization && ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization();
            terrain.setTooltip(Tooltip.create(Component.literal(!dimension.supportsRandomization
                    ? "Unavailable for this dimension's generator."
                    : "Enable World Type Randomization in the mod's experimental settings to randomize terrain profiles.")));
            addSetting("Terrain profiles", terrain, 20, this::terrainLabel);
            Button reset = button("Clear custom biome rules", b -> {
                selection().setText("[],[],[],[]");
                inherit.setMessage(Component.literal(inheritanceLabel()));
            });
            reset.setTooltip(Tooltip.create(Component.literal("Use the ON / SAFE mode defaults without inheriting Global.")));
            addSetting("Reset", reset);
        }
        list.setScrollAmount(settingsScroll);
    }

    private String inheritanceLabel() { return inherited() ? "Biomes: inherit Global" : "Biomes: custom"; }
    private String seedLabel() { return dimension.seedSalt == 0 ? "World seed" : "Randomized per region"; }
    private String terrainLabel() { return dimension.terrainSalt == 0 ? "Normal terrain" : "Randomized per region"; }

    private Button button(String label, Button.OnPress action) {
        return Button.builder(Component.literal(label), action).bounds(0, 0, 200, 20).build();
    }

    private void addRegionSetting(String caption, String value, Consumer<String> change) {
        EditBox box = new EditBox(font, 0, 0, 200, 20, Component.literal(caption));
        box.setMaxLength(9);
        box.setValue(value);
        box.setResponder(change);
        box.setTooltip(Tooltip.create(Component.literal("Applies to all dimensions. Values are limited to 1-512 chunks when saved.")));
        addSetting(caption, box, 200, null);
    }

    private void addSetting(String caption, AbstractWidget widget) {
        addSetting(caption, widget, 0, null);
    }

    private void addSetting(String caption, AbstractWidget widget, int preferredWidth, Supplier<String> stateLabel) {
        list.add(new PresetList.Row(widget) {
            @Override
            public void extractContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, boolean hovered, float partialTick) {
                gfx.text(font, caption, getContentX(), getContentY(), 0xFFB9C3CF, false);
                widget.setPosition(getContentX(), getContentY() + 13);
                widget.setWidth(preferredWidth == 0 ? getContentWidth() : Math.min(preferredWidth, getContentWidth()));
                if (stateLabel != null) {
                    int labelX = getContentX() + widget.getWidth() + 8;
                    gfx.text(font, ellipsize(stateLabel.get(), getContentRight() - labelX), labelX,
                            getContentY() + 19, widget.active ? 0xFFFFFFFF : 0xFF888888, false);
                }
                drawWidgets(gfx, mouseX, mouseY, partialTick);
            }
        }, 44);
    }

    private static final class SeedRandomizerButton extends Button {
        private boolean randomized;
        private final boolean compatible;

        SeedRandomizerButton(boolean randomized, boolean compatible, OnPress onPress) {
            super(0, 0, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.compatible = compatible;
            this.active = compatible;
            setRandomized(randomized);
        }

        void setRandomized(boolean randomized) {
            this.randomized = compatible && randomized;
            setMessage(Component.literal(!compatible ? "Region seed: unavailable"
                    : this.randomized ? "Region seed: randomized" : "Region seed: world seed"));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(gfx);
            int x = getX() + 6;
            int y = getY() + 5;
            if (!compatible || !randomized) {
                gfx.fill(x, y, x + 10, y + 10, compatible ? 0xFFFFFFFF : 0xFFFF3333);
                return;
            }
            var theme = SeedRandomizerButtonTheme.activeTheme();
            int[] colors = theme.colors();
            int offset = (int) ((System.currentTimeMillis() / theme.frameMs()) % colors.length);
            for (int i = 0; i < colors.length; i++) {
                gfx.fill(x + i * 10 / colors.length, y, x + (i + 1) * 10 / colors.length, y + 10,
                        colors[(i + offset) % colors.length]);
            }
        }
    }

    private static final class TerrainProfileButton extends Button {
        private boolean randomized;

        TerrainProfileButton(boolean randomized, OnPress onPress) {
            super(0, 0, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            setRandomized(randomized);
        }

        void setRandomized(boolean randomized) {
            this.randomized = randomized;
            setMessage(Component.literal(randomized ? "Terrain profiles: randomized" : "Terrain profiles: normal"));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(gfx);
            gfx.centeredText(Minecraft.getInstance().font, Component.literal(randomized ? "፠" : "᪠"),
                    getX() + getWidth() / 2, getY() + 6, !active ? 0xFF888888 : randomized ? 0xFFFFD36A : 0xFFFFFFFF);
        }
    }

    private String ellipsize(String text, int availableWidth) {
        return font.width(text) <= availableWidth ? text : font.plainSubstrByWidth(text, Math.max(0, availableWidth - font.width("..."))) + "...";
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void resize(int width, int height) {
        rememberScroll();
        super.resize(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(font, Component.literal(ellipsize(title.getString(), width - 28)), width / 2, 10, 0xFFFFFFFF);
        String subtitle = dimension == null ? "Default biome rules for dimensions without overrides"
                : inherited() ? "Using Global biome rules" : "Custom biome rules / mode: " + dimension.mode;
        gfx.centeredText(font, Component.literal(ellipsize(subtitle, width - 28)), width / 2, 24, 0xFFB9C3CF);
        if (!settingsTab) {
            int right = list.getRowRight() - 2;
            gfx.text(font, "Biome / #tag (" + matches + ")", list.getRowLeft() + 2, 92, 0xFFB9C3CF, false);
            gfx.centeredText(font, Component.literal("Whitelist"), right - RULE_WIDTH * 3 / 2 - RULE_GAP, 92, 0xFFB9C3CF);
            gfx.centeredText(font, Component.literal("Blacklist"), right - RULE_WIDTH / 2, 92, 0xFFB9C3CF);
            if (matches == 0) gfx.centeredText(font, Component.literal("No matching biomes or tags"), width / 2, 115, 0xFFB9C3CF);
            if (showText()) gfx.text(font, "Editable rules (hover for format)", list.getRowLeft(), height - 73, 0xFFB9C3CF, false);
        }
    }
}
