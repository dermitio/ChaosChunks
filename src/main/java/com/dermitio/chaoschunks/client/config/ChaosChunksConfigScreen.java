package com.dermitio.chaoschunks.client.config;

import com.dermitio.chaoschunks.client.sound.ChaosChunksSoundListScreen;
import com.dermitio.chaoschunks.client.sound.ChaosChunksSoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// =========
// Client config hub for opening music and sound toggle lists //
// =========
public class ChaosChunksConfigScreen extends Screen {

    private final Screen parent;

    public ChaosChunksConfigScreen(Screen parent) {
        super(Component.literal("ChaosChunks Config"));
        this.parent = parent;
    }

    // =========
    // Builds navigation buttons for the two sound configuration lists //
    // =========
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(Button.builder(
                Component.literal("Music"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksSoundListScreen(this, true))
        ).bounds(centerX - 100, centerY - 37, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Sound"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksSoundListScreen(this, false))
        ).bounds(centerX - 100, centerY - 11, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("UI Events"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksUiEventListScreen(this))
        ).bounds(centerX - 100, centerY + 15, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Preferences"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksPreferencesScreen(this))
        ).bounds(centerX - 100, centerY + 41, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Defaults"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksDefaultsScreen(this))
        ).bounds(centerX - 100, centerY + 67, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Experimental"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksExperimentalScreen(this))
        ).bounds(centerX - 100, centerY + 93, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> {
                    ChaosChunksSoundConfig.stopPreview();
                    Minecraft.getInstance().setScreen(parent);
                }
        ).bounds(centerX - 100, centerY + 127, 200, 20).build());
    }
// 26.1 change here
@Override
public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(gfx, mouseX, mouseY, partialTick);
    gfx.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
