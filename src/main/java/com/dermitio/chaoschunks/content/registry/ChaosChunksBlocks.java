package com.dermitio.chaoschunks.content.registry;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.content.mint.MintBushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// =========
// Registers Chaos Chunks block content //
// =========
public final class ChaosChunksBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChaosChunks.MODID);

    public static final DeferredBlock<MintBushBlock> MINT_BUSH = BLOCKS.registerBlock(
            "mint_bush",
            MintBushBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollision()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    private ChaosChunksBlocks() {}
}
