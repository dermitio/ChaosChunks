package com.dermitio.chaoschunks.client.preset;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/** Native scrolling, clipping, scrollbar dragging, keyboard navigation and narration for preset pages. */
final class PresetList extends ContainerObjectSelectionList<PresetList.Row> {
    PresetList(int width, int top, int bottom) {
        super(Minecraft.getInstance(), width, Math.max(24, bottom - top), top, 28);
        centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return Math.min(760, width - 28);
    }

    void add(Row row, int height) {
        addEntry(row, height);
    }

    void reset() {
        setFocused(null);
        clearEntries();
    }

    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        final List<AbstractWidget> widgets;

        Row(AbstractWidget... widgets) {
            this.widgets = List.of(widgets);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }

        void drawWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            for (AbstractWidget widget : widgets) {
                widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
    }
}
