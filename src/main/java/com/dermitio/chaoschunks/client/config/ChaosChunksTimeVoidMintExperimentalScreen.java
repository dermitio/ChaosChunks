package com.dermitio.chaoschunks.client.config;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChaosChunksTimeVoidMintExperimentalScreen extends Screen {

    private final Screen parent;

    public ChaosChunksTimeVoidMintExperimentalScreen(Screen parent) {
        super(Component.literal("Experimental: Time, Void, Mint"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.min(this.height - 56, this.height / 2 + 42);

        addRenderableWidget(Button.builder(
                enabledText(),
                button -> {
                    ChaosChunksExperimentsConfig.setTimeVoidMint(!ChaosChunksExperimentsConfig.timeVoidMint());
                    ChaosChunksExperimentsConfig.save();
                    button.setMessage(enabledText());
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX - 100, this.height - 28, 200, 20).build());
    }

    private Component enabledText() {
        return Component.literal("Time, Void, Mint: " + (ChaosChunksExperimentsConfig.timeVoidMint() ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        gfx.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        int maxWidth = Math.min(430, this.width - 40);
        int x = (this.width - maxWidth) / 2;
        drawWrapped(gfx, ChaosChunksExperimentsConfig.TIME_VOID_MINT_DESCRIPTION, x, 44, maxWidth, 0xFFFFD0D0);
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
