package com.dermitio.chaoschunks.content.mint;

import com.dermitio.chaoschunks.config.ChaosChunksExperimentsConfig;
import com.dermitio.chaoschunks.content.registry.ChaosChunksItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// =========
// Three-stage mint bush with shears-only harvesting and no natural generation //
// =========
public class MintBushBlock extends VegetationBlock {
    public static final MapCodec<MintBushBlock> CODEC = simpleCodec(MintBushBlock::new);
    private static final int TICKS_PER_MINECRAFT_DAY = 24000;
    private static final int SMALL_TO_HARVESTED_TICKS = TICKS_PER_MINECRAFT_DAY * 3;
    private static final int HARVESTED_TO_GROWN_TICKS = TICKS_PER_MINECRAFT_DAY;

    public static final int STAGE_SMALL = 0;
    public static final int STAGE_HARVESTED = 1;
    public static final int STAGE_GROWN = 2;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", STAGE_SMALL, STAGE_GROWN);

    private static final VoxelShape SHAPE_SMALL = Block.column(8.0, 0.0, 9.0);
    private static final VoxelShape SHAPE_GROWN = Block.column(14.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_HARVESTED = Block.column(12.0, 0.0, 13.0);

    @Override
    public MapCodec<MintBushBlock> codec() {
        return CODEC;
    }

    public MintBushBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, STAGE_SMALL));
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ChaosChunksItems.MINT_BUSH.get());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(STAGE)) {
            case STAGE_GROWN -> SHAPE_GROWN;
            case STAGE_HARVESTED -> SHAPE_HARVESTED;
            default -> SHAPE_SMALL;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        if (!state.is(oldState.getBlock())) {
            scheduleNextGrowth(level, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) return;

        int stage = state.getValue(STAGE);
        if (stage >= STAGE_GROWN) return;

        BlockState nextState = state.setValue(STAGE, stage + 1);
        level.setBlock(pos, nextState, 2);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(nextState));
        scheduleNextGrowth(level, pos, nextState);
    }

    // =========
    // Harvests grown mint with shears while leaving the bush in its harvested stage //
    // =========
    @Override
    protected InteractionResult useItemOn(
            ItemStack itemStack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!ChaosChunksExperimentsConfig.timeVoidMint()) {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }

        if (!itemStack.is(Items.SHEARS) || state.getValue(STAGE) != STAGE_GROWN) {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            int count = 2 + serverLevel.getRandom().nextInt(4);
            Block.popResource(serverLevel, pos, new ItemStack(ChaosChunksItems.MINT.get(), count));

            BlockState harvestedState = state.setValue(STAGE, STAGE_HARVESTED);
            serverLevel.setBlock(pos, harvestedState, 2);
            scheduleNextGrowth(serverLevel, pos, harvestedState);
            serverLevel.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, harvestedState));

            itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    private static void scheduleNextGrowth(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;

        int stage = state.getValue(STAGE);
        if (stage == STAGE_SMALL) {
            level.scheduleTick(pos, state.getBlock(), SMALL_TO_HARVESTED_TICKS);
        } else if (stage == STAGE_HARVESTED) {
            level.scheduleTick(pos, state.getBlock(), HARVESTED_TO_GROWN_TICKS);
        }
    }
}
