package com.dermitio.chaoschunks.client.config;

import com.dermitio.chaoschunks.client.sound.ChaosChunksMusicMixer;
import com.dermitio.chaoschunks.client.sound.ChaosChunksSoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChaosChunksPreferencesScreen extends Screen {
    private final Screen parent;

    public ChaosChunksPreferencesScreen(Screen parent) {
        super(Component.literal("ChaosChunks Preferences"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(Button.builder(
                overTheChunkModeText(),
                button -> {
                    ChaosChunksSoundConfig.cycleOverTheChunkMode();
                    ChaosChunksSoundConfig.save();
                    ChaosChunksMusicMixer.resetPlayback();
                    button.setMessage(overTheChunkModeText());
                }
        ).bounds(centerX - 120, centerY - 12, 240, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, centerY + 22, 200, 20).build());
    }

    private Component overTheChunkModeText() {
        return Component.literal("Over the Chunk: " + ChaosChunksSoundConfig.overTheChunkMode().label());
    }

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
