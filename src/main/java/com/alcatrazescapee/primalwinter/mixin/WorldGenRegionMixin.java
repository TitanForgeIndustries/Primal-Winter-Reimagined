package com.alcatrazescapee.primalwinter.mixin;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.alcatrazescapee.primalwinter.blocks.PrimalWinterBlocks;
import com.alcatrazescapee.primalwinter.util.Helpers;
import com.alcatrazescapee.primalwinter.util.WeatherHelper;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin
{
    @Shadow @Final private ServerLevel level;

    @Redirect(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState replaceTreeBlocksInWinterBiomes(ChunkAccess chunk, BlockPos pos, BlockState stateIn, boolean moved)
    {
        if (!WeatherHelper.isWinterActive(level) || !WeatherHelper.isWinterBiome(level, pos))
        {
            return chunk.setBlockState(pos, stateIn, moved);
        }
        final Supplier<Block> block = PrimalWinterBlocks.SNOWY_TREE_BLOCKS.get(stateIn.getBlock());
        if (block == null)
        {
            return chunk.setBlockState(pos, stateIn, moved);
        }
        final BlockState replacementState = block.get().defaultBlockState();
        return chunk.setBlockState(pos, Helpers.copyProperties(stateIn, replacementState), moved);
    }
}
