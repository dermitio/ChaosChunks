package com.dermitio.chaoschunks.client.config;

import com.dermitio.chaoschunks.client.preset.SeedRandomizerButtonTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

// =========
// Client config list for enabling or disabling date-based UI event themes //
// =========
public class ChaosChunksUiEventListScreen extends Screen {

    private static final int PREVIEW_SIZE = 14;
    private static final int PREVIEW_X_OFFSET = 84;
    private static final int PREVIEW_GAP = 4;
    private static final int ROW_START_Y = 40;
    private static final int ROW_HEIGHT = 24;
    private static final int BOTTOM_RESERVED_HEIGHT = 36;

    private final Screen parent;
    private List<ChaosChunksUiEventConfig.Entry> entries;
    private int scrollOffset;

    public ChaosChunksUiEventListScreen(Screen parent) {
        super(Component.literal("ChaosChunks UI Events"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.entries = ChaosChunksUiEventConfig.entries();
        this.scrollOffset = clampScrollOffset(scrollOffset);

        int centerX = this.width / 2;
        int y = ROW_START_Y;
        int end = Math.min(entries.size(), scrollOffset + visibleRows());

        for (int i = scrollOffset; i < end; i++) {
            ChaosChunksUiEventConfig.Entry entry = entries.get(i);
            addRenderableWidget(Button.builder(
                    Component.literal(entry.label()),
                    button -> {}
            ).bounds(centerX - 155, y, 170, 20).build());

            addRenderableWidget(Button.builder(
                    toggleText(entry.key()),
                    button -> {
                        boolean newValue = !ChaosChunksUiEventConfig.isEnabled(entry.key());
                        ChaosChunksUiEventConfig.setEnabled(entry.key(), newValue);
                        ChaosChunksUiEventConfig.save();
                        button.setMessage(toggleText(entry.key()));
                    }
            ).bounds(centerX + 20, y, 50, 20).build());

            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
    }

    private Component toggleText(String key) {
        return Component.literal(ChaosChunksUiEventConfig.isEnabled(key) ? "ON" : "OFF");
    }

    private int visibleRows() {
        return Math.max(1, (this.height - ROW_START_Y - BOTTOM_RESERVED_HEIGHT) / ROW_HEIGHT);
    }

    private int maxScrollOffset() {
        if (entries == null) {
            return 0;
        }
        return Math.max(0, entries.size() - visibleRows());
    }

    private int clampScrollOffset(int offset) {
        return Math.max(0, Math.min(offset, maxScrollOffset()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = maxScrollOffset();
        if (maxScroll > 0 && scrollY != 0.0D) {
            int direction = scrollY < 0.0D ? 1 : -1;
            int nextOffset = Math.max(0, Math.min(scrollOffset + direction, maxScroll));
            if (nextOffset != scrollOffset) {
                scrollOffset = nextOffset;
                rebuildWidgets();
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        if (entries == null) return;

        int centerX = this.width / 2;
        int x = centerX + PREVIEW_X_OFFSET;
        int y = ROW_START_Y + 3;
        int end = Math.min(entries.size(), scrollOffset + visibleRows());

        for (int i = scrollOffset; i < end; i++) {
            ChaosChunksUiEventConfig.Entry entry = entries.get(i);
            drawPreview(gfx, entry.key(), x, y);
            y += ROW_HEIGHT;
        }
    }

    private static void drawPreview(GuiGraphicsExtractor gfx, String key, int x, int y) {
        if ("pride_month".equals(key)) {
            SeedRandomizerButtonTheme.Theme[] themes = SeedRandomizerButtonTheme.pridePreviewThemes();
            for (int i = 0; i < themes.length; i++) {
                drawTheme(gfx, themes[i], x + i * (PREVIEW_SIZE + PREVIEW_GAP), y);
            }
            return;
        }

        SeedRandomizerButtonTheme.Theme theme = SeedRandomizerButtonTheme.previewTheme(key);
        drawTheme(gfx, theme, x, y);
    }

    private static void drawTheme(GuiGraphicsExtractor gfx, SeedRandomizerButtonTheme.Theme theme, int x, int y) {
        int[] colors = theme.colors();
        int offset = (int) ((System.currentTimeMillis() / theme.frameMs()) % colors.length);

        gfx.fill(x - 1, y - 1, x + PREVIEW_SIZE + 1, y + PREVIEW_SIZE + 1, 0xFF555555);
        for (int i = 0; i < colors.length; i++) {
            int sx0 = x + (i * PREVIEW_SIZE / colors.length);
            int sx1 = x + ((i + 1) * PREVIEW_SIZE / colors.length);
            gfx.fill(sx0, y, sx1, y + PREVIEW_SIZE, colors[(i + offset) % colors.length]);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
