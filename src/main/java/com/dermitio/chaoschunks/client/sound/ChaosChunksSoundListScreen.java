package com.dermitio.chaoschunks.client.sound;

import com.dermitio.chaoschunks.client.ChaosChunksClient;
import com.dermitio.chaoschunks.network.ChaosChunksSetSoundTogglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class ChaosChunksSoundListScreen extends Screen {

    private static final int ROW_START_Y = 40;
    private static final int ROW_HEIGHT = 24;
    private static final int BOTTOM_RESERVED_HEIGHT = 36;

    private final Screen parent;
    private final boolean music;
    private List<ChaosChunksSoundConfig.Entry> entries;
    private int scrollOffset;

    public ChaosChunksSoundListScreen(Screen parent, boolean music) {
        super(Component.literal(music ? "ChaosChunks Music" : "ChaosChunks Sounds"));
        this.parent = parent;
        this.music = music;
    }

    @Override
    protected void init() {
        this.entries = ChaosChunksSoundConfig.loadEntries(music);
        this.scrollOffset = clampScrollOffset(scrollOffset);

        int centerX = this.width / 2;
        int y = ROW_START_Y;
        int end = Math.min(entries.size(), scrollOffset + visibleRows());

        for (int i = scrollOffset; i < end; i++) {
            ChaosChunksSoundConfig.Entry entry = entries.get(i);
            String displayName = soundName(entry);

            addRenderableWidget(Button.builder(
                    Component.literal(displayName),
                    button -> {}
            ).bounds(centerX - 155, y, 170, 20).build());

            addRenderableWidget(Button.builder(
                    toggleText(entry.key()),
                    button -> {
                        boolean newValue = !ChaosChunksSoundConfig.isEnabled(entry.key());
                        ChaosChunksSoundConfig.setEnabled(entry.key(), newValue);
                        ChaosChunksSoundConfig.save();
                        if (music) {
                            ChaosChunksMusicMixer.resetPlayback();
                        }

                        Minecraft mc = Minecraft.getInstance();

                        if (ChaosChunksClient.canSendSoundToggle(mc)) {
                            mc.getConnection().send(
                                    new ChaosChunksSetSoundTogglePayload(entry.key(), newValue)
                            );
                        } else if (mc.getConnection() == null) {
                            ChaosChunksPendingSoundToggles.queue(entry.key(), newValue);
                        }

                        button.setMessage(toggleText(entry.key()));
                    }
            ).bounds(centerX + 20, y, 50, 20).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Preview"),
                    button -> ChaosChunksSoundConfig.playPreview(entry)
            ).bounds(centerX + 75, y, 60, 20).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Stop"),
                    button -> ChaosChunksSoundConfig.stopPreview()
            ).bounds(centerX + 140, y, 50, 20).build());

            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> {
                    ChaosChunksSoundConfig.stopPreview();
                    Minecraft.getInstance().setScreen(parent);
                }
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
    }

    private Component toggleText(String key) {
        return Component.literal(ChaosChunksSoundConfig.isEnabled(key) ? "ON" : "OFF");
    }

    private static String soundName(ChaosChunksSoundConfig.Entry entry) {
        String path = entry.soundId().getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
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
    public void removed() {
        ChaosChunksSoundConfig.stopPreview();
        super.removed();
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        if (entries == null || entries.isEmpty()) {
            gfx.centeredText(
                    this.font,
                    Component.literal("No " + (music ? "music" : "sound") + " entries found in sounds.json"),
                    this.width / 2,
                    this.height / 2,
                    0xFFAAAAAA
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
