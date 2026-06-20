package com.dermitio.chaoschunks.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// =========
// Config tab for world-creation defaults applied to new ChaosChunks dimensions //
// =========
public class ChaosChunksDefaultsScreen extends Screen {

    private final Screen parent;

    public ChaosChunksDefaultsScreen(Screen parent) {
        super(Component.literal("ChaosChunks Defaults"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 36;

        addRenderableWidget(Button.builder(
                Component.literal("Default generation mode"),
                button -> {}
        ).bounds(centerX - 155, y, 170, 20).build());

        addRenderableWidget(Button.builder(
                defaultModeText(),
                button -> {
                    ChaosChunksDefaultsConfig.setDefaultDimensionMode(nextDefaultMode());
                    ChaosChunksDefaultsConfig.save();
                    button.setMessage(defaultModeText());
                }
        ).bounds(centerX + 20, y, 135, 20).build());

        y += 26;

        addRenderableWidget(Button.builder(
                Component.literal("Region seed mode"),
                button -> {}
        ).bounds(centerX - 155, y, 170, 20).build());

        addRenderableWidget(Button.builder(
                regionSeedModeText(),
                button -> {
                    ChaosChunksDefaultsConfig.setRegionSeedMode(nextRegionSeedMode());
                    ChaosChunksDefaultsConfig.save();
                    button.setMessage(regionSeedModeText());
                }
        ).bounds(centerX + 20, y, 135, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
    }

    private Component defaultModeText() {
        return Component.literal(ChaosChunksDefaultsConfig.defaultDimensionMode().label());
    }

    private ChaosChunksDefaultsConfig.DefaultDimensionMode nextDefaultMode() {
        return switch (ChaosChunksDefaultsConfig.defaultDimensionMode()) {
            case ON -> ChaosChunksDefaultsConfig.DefaultDimensionMode.SAFE;
            case SAFE -> ChaosChunksDefaultsConfig.DefaultDimensionMode.OFF;
            case OFF -> ChaosChunksDefaultsConfig.DefaultDimensionMode.ON;
        };
    }

    private Component regionSeedModeText() {
        return Component.literal(ChaosChunksDefaultsConfig.regionSeedMode().label());
    }

    private ChaosChunksDefaultsConfig.RegionSeedMode nextRegionSeedMode() {
        return switch (ChaosChunksDefaultsConfig.regionSeedMode()) {
            case WORLD_SEED -> ChaosChunksDefaultsConfig.RegionSeedMode.RANDOMIZED_REGION_SEED;
            case RANDOMIZED_REGION_SEED -> ChaosChunksDefaultsConfig.RegionSeedMode.WORLD_SEED;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
