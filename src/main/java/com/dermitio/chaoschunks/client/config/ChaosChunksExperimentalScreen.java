package com.dermitio.chaoschunks.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChaosChunksExperimentalScreen extends Screen {

    private final Screen parent;

    public ChaosChunksExperimentalScreen(Screen parent) {
        super(Component.literal("ChaosChunks Experimental"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(Button.builder(
                Component.literal("World Type"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksWorldTypeExperimentalScreen(this))
        ).bounds(centerX - 100, centerY - 29, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Time, Void, Mint"),
                button -> Minecraft.getInstance().setScreen(new ChaosChunksTimeVoidMintExperimentalScreen(this))
        ).bounds(centerX - 100, centerY - 3, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
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
