package com.dermitio.chaoschunks.server.voids;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import com.dermitio.chaoschunks.server.sound.ChaosChunksPlayerSoundPrefs;
import com.dermitio.chaoschunks.server.time.TimekeepUnlocks;
import com.dermitio.chaoschunks.sound.ChaosChunksSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// =========
// Grants weighted void gifts after walking through the void biome //
// =========
public final class VoidGiftEvent {

    public static final String EVENT_ID = "void_gift";
    private static final int CHECK_DISTANCE_BLOCKS = 10;
    private static final int TRIGGER_CHANCE_DENOMINATOR = 20;

    private static final Item[] LOGS = {
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
            Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
            Items.PALE_OAK_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM
    };
    private static final Item[] LEAVES = {
            Items.OAK_LEAVES, Items.SPRUCE_LEAVES, Items.BIRCH_LEAVES, Items.JUNGLE_LEAVES,
            Items.ACACIA_LEAVES, Items.DARK_OAK_LEAVES, Items.MANGROVE_LEAVES, Items.CHERRY_LEAVES,
            Items.PALE_OAK_LEAVES, Items.AZALEA_LEAVES, Items.FLOWERING_AZALEA_LEAVES
    };

    private static final Gift[] GIFTS = {
            new Gift(random -> new ItemStack(randomItem(random, LOGS), 5), 1000),
            new Gift(random -> new ItemStack(randomItem(random, LEAVES), 2), 450),
            new Gift(random -> new ItemStack(Items.RAW_IRON, 1), 175),
            new Gift(random -> new ItemStack(Items.REDSTONE, 2), 225),
            new Gift(random -> new ItemStack(Items.LAPIS_LAZULI, 1), 700),
            new Gift(random -> new ItemStack(Items.DIAMOND, 1), 10),
            new Gift(random -> new ItemStack(ChaosChunksItems.MINT.get(), 1), 1),
            new Gift(random -> new ItemStack(Items.SWEET_BERRIES, 3), 700),
            new Gift(random -> new ItemStack(Items.DEEPSLATE_GOLD_ORE, 1), 240),
            new Gift(random -> new ItemStack(Items.ICE, 1), 975),
            new Gift(random -> new ItemStack(Items.INK_SAC, 2), 665),
            new Gift(random -> new ItemStack(Items.SAND, 3), 455)
    };

    private static final Map<UUID, WalkState> WALK_STATES = new HashMap<>();

    private VoidGiftEvent() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(VoidGiftEvent::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(VoidGiftEvent::onPlayerLoggedOut);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WALK_STATES.remove(event.getEntity().getUUID());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) {
            if (event.getEntity() instanceof ServerPlayer player) {
                WALK_STATES.remove(player.getUUID());
            }
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 5 != 0) return;

        UUID playerId = player.getUUID();
        if (!level.getBiome(player.blockPosition()).is(Biomes.THE_VOID)) {
            WALK_STATES.remove(playerId);
            return;
        }

        WalkState state = WALK_STATES.computeIfAbsent(playerId, id -> new WalkState(player.blockPosition()));
        BlockPos current = player.blockPosition();
        int distance = horizontalDistance(state.lastPos, current);
        if (distance <= 0) return;

        state.lastPos = current.immutable();
        state.walkedBlocks += distance;

        while (state.walkedBlocks >= CHECK_DISTANCE_BLOCKS) {
            state.walkedBlocks -= CHECK_DISTANCE_BLOCKS;
            if (player.getRandom().nextInt(TRIGGER_CHANCE_DENOMINATOR) == 0) {
                grantGift(player);
                playGiftSound(player);
                TimekeepUnlocks.triggerEvent(level, EVENT_ID);
            }
        }
    }

    private static void grantGift(ServerPlayer player) {
        ItemStack gift = pickGift(player.getRandom());
        if (!player.getInventory().add(gift)) {
            player.drop(gift, false);
        }
    }

    private static void playGiftSound(ServerPlayer player) {
        var sound = ChaosChunksSounds.VOID_GIFT.get();
        String key = sound.location().getPath();
        if (!ChaosChunksPlayerSoundPrefs.isEnabled(player.getUUID(), key)) return;

        float pitch = 0.9F + (player.getRandom().nextFloat() * 0.2F);
        player.connection.send(new ClientboundSoundEntityPacket(
                Holder.direct(sound),
                SoundSource.PLAYERS,
                player,
                1.6F,
                pitch,
                player.getRandom().nextLong()
        ));
    }

    private static ItemStack pickGift(RandomSource random) {
        int totalWeight = 0;
        for (Gift gift : GIFTS) {
            totalWeight += gift.weight();
        }

        int roll = random.nextInt(totalWeight);
        for (Gift gift : GIFTS) {
            roll -= gift.weight();
            if (roll < 0) return gift.stackFactory().apply(random);
        }
        return GIFTS[0].stackFactory().apply(random);
    }

    private static Item randomItem(RandomSource random, Item[] items) {
        return items[random.nextInt(items.length)];
    }

    private static int horizontalDistance(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        return (int) Math.floor(Math.sqrt(dx * dx + dz * dz));
    }

    private record Gift(Function<RandomSource, ItemStack> stackFactory, int weight) {}

    private static final class WalkState {
        private BlockPos lastPos;
        private int walkedBlocks;

        private WalkState(BlockPos lastPos) {
            this.lastPos = lastPos.immutable();
        }
    }
}
