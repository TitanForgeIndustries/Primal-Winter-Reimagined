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
 * Shared, side-safe rules for deciding whether a location participates in Primal Winter weather.
 *
 * <p>The old implementation duplicated slightly different checks in the server tick, worldgen,
 * fog renderer, and precipitation renderer.  Apart from making those paths drift apart, that
 * meant that a modded dimension could be handled by one path and ignored by another.  Keeping the
 * dimension, biome, sky, and seasonal checks here makes the decision data-driven and works for
 * any registered dimension/biome pair rather than a fixed list of dimension IDs.</p>
 */
public final class WeatherHelper
{
    private WeatherHelper() {}

    /** Returns whether Primal Winter is enabled for this level's registered dimension. */
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

    /**
     * Returns the single authoritative seasonal state used by both server and client paths.
     *
     * <p>The day boundary is deliberately based on the world's persisted game time: the final
     * tick of day {@code N - 1} is still inactive, while tick {@code N * 24000} is active.  A
     * client level receives that same time from the server, so rendering cannot advance winter
     * using a local wall clock.</p>
     */
    public static boolean isWinterActive(Level level)
    {
        return supportsWinterWeather(level)
                && level.getDayTime() / 24000L >= Config.INSTANCE.snowStartDay.getAsInt();
    }

    /** Returns whether the biome at the position is enabled by the configured biome rules. */
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

    /**
     * Returns the runtime seasonal biome-overlay state at a position.  This is the authoritative
     * predicate for all systems that need to behave as though the underlying biome were a cold
     * Primal Winter biome.  The registry biome itself is intentionally never replaced or mutated.
     */
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

    /**
     * Returns the precipitation that the current level should expose to a caller that has both
     * the biome and level context.  Vanilla's Biome method cannot make this decision by itself
     * because it has no Level/day argument, so the overlay is applied at its context-bearing
     * call sites (vanilla renderer, server tick, and optional Particle Rain compatibility hook).
     */
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

    /**
     * Vanilla's freeze test is based on the biome's private temperature.  During the seasonal
     * overlay we retain all of vanilla's block/light/water safeguards but provide the cold
     * climate result dynamically, without changing the registry biome temperature.
     */
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

    /** Vanilla's snow-layer placement test with the seasonal cold-temperature check removed. */
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

    /**
     * Returns whether Primal Winter may place or render snow at a location.
     *
     * <p>The Forge biome modifier cannot receive a level or a day, so it must not rewrite biome
     * climate metadata globally.  Once winter is active, Primal Winter therefore supplies the
     * cold precipitation behavior at runtime for configured biomes.  The dimension's sky-light
     * capability and the configured biome/dimension exclusions remain authoritative.</p>
     */
    public static boolean canSnowAt(Level level, BlockPos pos)
    {
        return isPrimalWinterBiome(level, pos);
    }

    /** Returns whether the active Primal Winter storm is currently raining at the position. */
    public static boolean isSnowingAt(Level level, BlockPos pos)
    {
        if (!canSnowAt(level, pos) || !level.isRaining() || !level.canSeeSky(pos))
        {
            return false;
        }

        // Match vanilla's height guard without consulting Biome#getPrecipitationAt, because
        // enabled warm/dry biomes intentionally receive Primal Winter precipitation at runtime.
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY();
    }
}
