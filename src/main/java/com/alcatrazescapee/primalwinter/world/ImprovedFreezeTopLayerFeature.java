package com.alcatrazescapee.primalwinter.world;

import java.util.Arrays;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import com.alcatrazescapee.primalwinter.blocks.PrimalWinterBlocks;
import com.alcatrazescapee.primalwinter.util.Config;
import com.alcatrazescapee.primalwinter.util.WeatherHelper;


public class ImprovedFreezeTopLayerFeature extends Feature<NoneFeatureConfiguration>
{
    public ImprovedFreezeTopLayerFeature(Codec<NoneFeatureConfiguration> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        final WorldGenLevel level = context.level();
        if (!WeatherHelper.isWinterActive(level.getLevel()))
        {
            return false;
        }
        final BlockPos pos = context.origin();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();


        // First, find the highest and lowest exposed y pos in the chunk
        final int minY = level.getMinBuildHeight();
        int maxY = minY;
        for (int x = 0; x < 16; ++x)
        {
            for (int z = 0; z < 16; ++z)
            {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() + x, pos.getZ() + z);
                y = Math.min(level.getMaxBuildHeight() - 1, y);
                if (maxY < y)
                {
                    maxY = y;
                }
            }
        }

        // Then, step downwards, tracking the exposure to sky at each step
        int[] skyLights = new int[16 * 16], prevSkyLights = new int[16 * 16];
        final int[] lightQueue = new int[16 * 16];
        final boolean[] queued = new boolean[16 * 16];
        Arrays.fill(prevSkyLights, 7);
        for (int y = maxY; y >= minY; y--)
        {
            for (int x = 0; x < 16; ++x)
            {
                for (int z = 0; z < 16; ++z)
                {
                    final int skyLight = prevSkyLights[x + 16 * z];
                    cursor.set(pos.getX() + x, y, pos.getZ() + z);
                    final BlockState state = level.getBlockState(cursor);
                    if (state.isAir())
                    {
                        // Continue skylight downwards
                        skyLights[x + 16 * z] = prevSkyLights[x + 16 * z];
                        extendSkyLights(skyLights, x, z, lightQueue, queued);
                    }
                    if (skyLight > 0)
                    {
                        placeSnowAndIce(level, cursor, state, context.random(), skyLight);
                    }
                }
            }

            // Break early if all possible sky light is gone
            boolean hasSkyLight = false;
            for (int i = 0; i < 16 * 16; i++)
            {
                if (skyLights[i] > 0)
                {
                    hasSkyLight = true;
                    break; // exit checking loop, continue with y loop
                }
            }
            if (!hasSkyLight)
            {
                break; // exit y loop
            }

            // Copy sky lights into previous and reset current sky lights
            System.arraycopy(skyLights, 0, prevSkyLights, 0, skyLights.length);
            Arrays.fill(skyLights, 0);
        }
        return true;
    }

    /**
     * Simple BFS that extends a skylight source outwards within the array
     */
    private void extendSkyLights(int[] skyLights, int startX, int startZ, int[] positions, boolean[] queued)
    {
        if (skyLights[startX + 16 * startZ] <= 1)
        {
            return;
        }
        Arrays.fill(queued, false);
        int head = 0;
        int tail = 0;
        final int start = startX + 16 * startZ;
        positions[tail++] = start;
        queued[start] = true;
        while (head < tail)
        {
            final int position = positions[head++];
            final int positionX = position % 16;
            final int positionZ = position / 16;
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                final int nextX = positionX + direction.getStepX();
                final int nextZ = positionZ + direction.getStepZ();
                final int nextSkyLight = skyLights[position] - 1;
                if (nextX >= 0 && nextX < 16 && nextZ >= 0 && nextZ < 16 && skyLights[nextX + 16 * nextZ] < nextSkyLight)
                {
                    final int next = nextX + 16 * nextZ;
                    skyLights[next] = nextSkyLight;
                    if (!queued[next])
                    {
                        positions[tail++] = next;
                        queued[next] = true;
                    }
                }
            }
        }
    }

    private void placeSnowAndIce(WorldGenLevel level, BlockPos pos, BlockState state, RandomSource random, int skyLight)
    {
        if (!WeatherHelper.canSnowAt(level.getLevel(), pos))
        {
            return;
        }

        final FluidState fluidState = level.getFluidState(pos);
        final BlockPos posDown = pos.below();
        final BlockState stateDown = level.getBlockState(posDown);

        // First, possibly replace the block below. This may have impacts on being able to add snow on top
        if (state.isAir())
        {
            final Block replacementBlock = PrimalWinterBlocks.SNOWY_SPECIAL_TERRAIN_BLOCKS.getOrDefault(stateDown.getBlock(), () -> null).get();
            if (replacementBlock != null)
            {
                BlockState replacementState = replacementBlock.defaultBlockState();
                level.setBlock(posDown, replacementState, 2);
            }
        }

        // Then, try and place snow layers / ice at the current location
        if (fluidState.getType() == Fluids.WATER && (state.getBlock() instanceof LiquidBlock || state.canBeReplaced()))
        {
            level.setBlock(pos, Blocks.ICE.defaultBlockState(), 2);
            if (!(state.getBlock() instanceof LiquidBlock))
            {
                level.scheduleTick(pos, Blocks.ICE, 0);
            }
        }
        else if (fluidState.getType() == Fluids.LAVA && state.getBlock() instanceof LiquidBlock)
        {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 2);
        }
        else if (Blocks.SNOW.defaultBlockState().canSurvive(level, pos) && state.canBeReplaced())
        {
            // Special exceptions
            BlockPos posUp = pos.above();
            if (state.getBlock() instanceof DoublePlantBlock && level.getBlockState(posUp).getBlock() == state.getBlock())
            {
                // Remove the above plant
                level.removeBlock(posUp, false);
            }

            int layers;
            if (Config.INSTANCE.enableSnowAccumulationDuringWorldgen.getAsBoolean())
            {
                layers = Mth.clamp(skyLight - random.nextInt(3) - countExposedFaces(level, pos), 1, 7);
            }
            else
            {
                layers = 1;
            }
            level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, layers), 3);

            // Replace the below block as well
            Block replacementBlock = PrimalWinterBlocks.SNOWY_TERRAIN_BLOCKS.getOrDefault(stateDown.getBlock(), () -> null).get();
            if (replacementBlock != null)
            {
                BlockState replacementState = replacementBlock.defaultBlockState();
                level.setBlock(posDown, replacementState, 2);
            }
        }
    }

    private int countExposedFaces(WorldGenLevel level, BlockPos pos)
    {
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            BlockPos posAt = pos.relative(direction);
            if (!level.getBlockState(posAt).isFaceSturdy(level, posAt, direction.getOpposite()))
            {
                count++;
            }
        }
        return count;
    }
}
