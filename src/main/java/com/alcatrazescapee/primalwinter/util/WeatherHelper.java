package com.alcatrazescapee.primalwinter.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;

/**
 * Shared rules for deciding whether a location participates in Primal Winter weather.
 *
 * <p>This mirror is kept in sync with the active Common source tree for tools that inspect the
 * legacy single-source layout. The Forge build uses the Common/Forge source sets.</p>
 */
public final class WeatherHelper
{
    private WeatherHelper() {}

    public static boolean isWinterDimension(Level level)
    {
        return level != null && Config.INSTANCE.isWinterDimension(level.dimension());
    }

    /** Returns whether this level can actually host sky-driven winter weather. */
    public static boolean supportsWinterWeather(Level level)
    {
        return isWinterDimension(level) && level.dimensionType().hasSkyLight();
    }

    /**
     * Vanilla weather timers are writable only for a level backed by its own level data.  Forge
     * represents secondary dimensions with {@link DerivedLevelData}; its weather mutators are
     * intentionally no-ops and its game rules are shared with the primary level.  Do not let a
     * modded dimension toggle that shared state while still allowing its winter predicate and
     * world-generation rules to be evaluated normally.
     */
    public static boolean canControlWeather(Level level)
    {
        return level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && supportsWinterWeather(level)
                && !(serverLevel.getLevelData() instanceof DerivedLevelData);
    }

    /** Returns the server/world-time authoritative winter state used by every winter path. */
    public static boolean isWinterActive(Level level)
    {
        return supportsWinterWeather(level)
                && level.getDayTime() / 24000L >= Config.INSTANCE.snowStartDay.getAsInt();
    }

    public static boolean isWinterBiome(Level level, BlockPos pos)
    {
        if (level == null || pos == null || !isWinterDimension(level))
        {
            return false;
        }
        return level.getBiome(pos).unwrapKey()
                .map(key -> Config.INSTANCE.isWinterBiome(key.location()))
                .orElse(false);
    }

    /** Returns the runtime seasonal biome-overlay state at a position. */
    public static boolean isPrimalWinterBiome(Level level, BlockPos pos)
    {
        return isWinterActive(level) && pos != null && isWinterBiome(level, pos);
    }

    /** Resolves the owning server level for a normal level or a world-generation view. */
    public static Level seasonalLevel(LevelReader reader)
    {
        if (reader instanceof Level level)
        {
            return level;
        }
        if (reader instanceof ServerLevelAccessor accessor)
        {
            return accessor.getLevel();
        }
        return null;
    }

    /** Returns context-aware seasonal precipitation without mutating biome registry data. */
    public static Biome.Precipitation getPrecipitation(Level level, Biome biome, BlockPos pos)
    {
        return isPrimalWinterBiome(level, pos)
                ? Biome.Precipitation.SNOW
                : biome.getPrecipitationAt(pos);
    }

    /** Same precipitation decision for Particle Rain's Holder-based compatibility API. */
    public static Biome.Precipitation getPrecipitation(Level level, Holder<Biome> biome, BlockPos pos)
    {
        return getPrecipitation(level, biome.value(), pos);
    }

    /** Vanilla freeze checks with the seasonal cold-temperature result supplied dynamically. */
    public static boolean canFreeze(LevelReader reader, BlockPos pos, boolean requireNeighbours)
    {
        if (pos.getY() < reader.getMinBuildHeight() || pos.getY() >= reader.getMaxBuildHeight()
                || reader.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) >= 10)
        {
            return false;
        }
        final FluidState fluid = reader.getFluidState(pos);
        if (fluid.getType() != Fluids.WATER || !(reader.getBlockState(pos).getBlock() instanceof LiquidBlock))
        {
            return false;
        }
        if (!requireNeighbours)
        {
            return true;
        }
        return reader.isWaterAt(pos.west())
                && reader.isWaterAt(pos.east())
                && reader.isWaterAt(pos.north())
                && reader.isWaterAt(pos.south());
    }

    /** Vanilla snow-layer placement checks with the biome temperature gate removed. */
    public static boolean canPlaceSnow(LevelReader reader, BlockPos pos)
    {
        if (pos.getY() < reader.getMinBuildHeight() || pos.getY() >= reader.getMaxBuildHeight()
                || reader.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) >= 10)
        {
            return false;
        }
        final var state = reader.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.SNOW))
                && Blocks.SNOW.defaultBlockState().canSurvive(reader, pos);
    }

    public static boolean canSnowAt(Level level, BlockPos pos)
    {
        return isPrimalWinterBiome(level, pos);
    }

    public static boolean isSnowingAt(Level level, BlockPos pos)
    {
        if (!canSnowAt(level, pos) || !level.isRaining() || !level.canSeeSky(pos))
        {
            return false;
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }
}
