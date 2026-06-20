package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.deepbreath.DeepbreathItem;
import com.dermitio.chaoschunks.content.time.TimeBookItem;
import com.dermitio.chaoschunks.content.voids.VoidEssenceItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// =========
// Registers Chaos Chunks item content and creative tab entries //
// =========
public final class ChaosChunksItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChaosChunks.MODID);
    private static final int MINT_FRESHNESS_TICKS = 100;
    private static final int DEEPBREATH_TICKS = 20 * 40;
    private static final int DEEPBREATH_LONG_TICKS = 20 * 80;

    private static final FoodProperties MINT_FOOD = new FoodProperties.Builder()
            .nutrition(1)
            .saturationModifier(0.1F)
            .alwaysEdible()
            .build();

    private static final Consumable MINT_CONSUMABLE = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(ChaosChunksEffects.FRESHNESS, MINT_FRESHNESS_TICKS)))
            .build();

    private static final FoodProperties DEEPBREATH_FOOD = new FoodProperties.Builder()
            .alwaysEdible()
            .build();

    public static final DeferredItem<Item> MINT =
            ITEMS.registerItem("mint", Item::new, () -> new Item.Properties().food(MINT_FOOD, MINT_CONSUMABLE));

    public static final DeferredItem<Item> VOID_ESSENCE =
            ITEMS.registerItem("void_essence", VoidEssenceItem::new, () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> TIME_BOOK =
            ITEMS.registerItem("time_book", TimeBookItem::new, () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> DEEPBREATH =
            ITEMS.registerItem("deepbreath", properties -> new DeepbreathItem(properties, DEEPBREATH_TICKS, 0, ChatFormatting.DARK_AQUA),
                    () -> deepbreathProperties(DEEPBREATH_TICKS, 0));

    public static final DeferredItem<Item> DEEPBREATH_STRONG =
            ITEMS.registerItem("deepbreath_strong", properties -> new DeepbreathItem(properties, DEEPBREATH_TICKS, 1, ChatFormatting.AQUA),
                    () -> deepbreathProperties(DEEPBREATH_TICKS, 1));

    public static final DeferredItem<Item> DEEPBREATH_LONG =
            ITEMS.registerItem("deepbreath_long", properties -> new DeepbreathItem(properties, DEEPBREATH_LONG_TICKS, 0, ChatFormatting.DARK_AQUA),
                    () -> deepbreathProperties(DEEPBREATH_LONG_TICKS, 0));

    public static final DeferredItem<BlockItem> MINT_BUSH = ITEMS.registerSimpleBlockItem(ChaosChunksBlocks.MINT_BUSH);

    private static Item.Properties deepbreathProperties(int durationTicks, int amplifier) {
        Consumable consumable = Consumables.defaultDrink()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(ChaosChunksEffects.FRESHNESS, durationTicks, amplifier)))
                .build();
        return new Item.Properties()
                .stacksTo(1)
                .usingConvertsTo(Items.GLASS_BOTTLE)
                .food(DEEPBREATH_FOOD, consumable);
    }

    // =========
    // Adds mint content to vanilla creative tabs without creating a custom tab //
    // =========
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(new ItemStack(MINT_BUSH.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(new ItemStack(MINT.get()));
            event.accept(new ItemStack(VOID_ESSENCE.get()));
            event.accept(new ItemStack(TIME_BOOK.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(new ItemStack(DEEPBREATH.get()));
            event.accept(new ItemStack(DEEPBREATH_STRONG.get()));
            event.accept(new ItemStack(DEEPBREATH_LONG.get()));
        }
    }

    private ChaosChunksItems() {}
}
