package com.dermitio.chaoschunks.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ChaosChunksWorldTypeExperimentalScreen extends Screen {

    private static final List<String> WARNING_LINES = List.of(
            "This option is to prevent users from blindly using the world type randomization in its eary experimental state.",
            "World type randomization, however cool it sounds, is not stable whatsoever and is completely broken when introduced to custom dimensions, even causing generation to hang entirely.",
            "if you see these risks and still wish to proceed enable this option and then create a new world with world type randomization on."
    );

    private final Screen parent;

    public ChaosChunksWorldTypeExperimentalScreen(Screen parent) {
        super(Component.literal("Experimental: World Type"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.min(this.height - 82, this.height / 2 + 42);

        addRenderableWidget(Button.builder(
                enabledText(),
                button -> {
                    boolean next = !ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization();
                    ChaosChunksDefaultsConfig.setExperimentalWorldTypeRandomization(next);
                    if (!next) {
                        ChaosChunksDefaultsConfig.setTerrainProfileMode(ChaosChunksDefaultsConfig.TerrainProfileMode.NORMAL);
                    }
                    ChaosChunksDefaultsConfig.save();
                    button.setMessage(enabledText());
                    Minecraft.getInstance().setScreen(new ChaosChunksWorldTypeExperimentalScreen(parent));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 26;

        Button defaultButton = Button.builder(
                terrainProfileModeText(),
                button -> {
                    if (!ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization()) return;
                    ChaosChunksDefaultsConfig.setTerrainProfileMode(nextTerrainProfileMode());
                    ChaosChunksDefaultsConfig.save();
                    button.setMessage(terrainProfileModeText());
                }
        ).bounds(centerX - 100, y, 200, 20).build();
        defaultButton.active = ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization();
        addRenderableWidget(defaultButton);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
    }

    private Component enabledText() {
        return Component.literal("World type UI: " + (ChaosChunksDefaultsConfig.experimentalWorldTypeRandomization() ? "ON" : "OFF"));
    }

    private Component terrainProfileModeText() {
        return Component.literal("New world default: " + ChaosChunksDefaultsConfig.terrainProfileMode().label());
    }

    private ChaosChunksDefaultsConfig.TerrainProfileMode nextTerrainProfileMode() {
        return switch (ChaosChunksDefaultsConfig.terrainProfileMode()) {
            case NORMAL -> ChaosChunksDefaultsConfig.TerrainProfileMode.RANDOMIZED_TERRAIN_PROFILES;
            case RANDOMIZED_TERRAIN_PROFILES -> ChaosChunksDefaultsConfig.TerrainProfileMode.NORMAL;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        int maxWidth = Math.min(430, this.width - 40);
        int x = (this.width - maxWidth) / 2;
        int y = 44;
        for (String paragraph : WARNING_LINES) {
            y = drawWrapped(gfx, paragraph, x, y, maxWidth, 0xFFFFD0D0) + 8;
        }
    }

    private int drawWrapped(GuiGraphicsExtractor gfx, String text, int x, int y, int width, int color) {
        String[] words = text.split(" ");
        String line = "";
        for (String word : words) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (this.font.width(next) > width && !line.isEmpty()) {
                gfx.text(this.font, Component.literal(line), x, y, color, false);
                y += 10;
                line = word;
            } else {
                line = next;
            }
        }

        if (!line.isEmpty()) {
            gfx.text(this.font, Component.literal(line), x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
