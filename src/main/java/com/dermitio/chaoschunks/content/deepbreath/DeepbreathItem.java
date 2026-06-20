package com.dermitio.chaoschunks.content.deepbreath;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class DeepbreathItem extends Item {

    private final int durationTicks;
    private final int amplifier;
    private final ChatFormatting nameColor;

    public DeepbreathItem(Properties properties, int durationTicks, int amplifier, ChatFormatting nameColor) {
        super(properties);
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.nameColor = nameColor;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId()).withStyle(nameColor);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("item.chaoschunks.deepbreath.flavor").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.translatable(
                "item.chaoschunks.deepbreath.desc",
                amplifier + 1,
                durationTicks / 20
        ).withStyle(ChatFormatting.GRAY));
    }
}
