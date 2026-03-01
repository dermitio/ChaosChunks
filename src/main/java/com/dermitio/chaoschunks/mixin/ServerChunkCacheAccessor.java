package com.dermitio.chaoschunks.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

    // ** Exposes the private chunkMap field from ServerChunkCache for runtime access **
    @Accessor("chunkMap")
    ChunkMap chaoschunks$getChunkMap();
}
