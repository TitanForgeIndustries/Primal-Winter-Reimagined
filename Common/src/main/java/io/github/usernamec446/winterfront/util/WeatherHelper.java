package io.github.usernamec446.winterfront.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;

import io.github.usernamec446.winterfront.climate.ClimateConfig;
import io.github.usernamec446.winterfront.compat.SeasonCompat;
import io.github.usernamec446.winterfront.storm.SnowSettings;
import io.github.usernamec446.winterfront.storm.StormProfile;
import io.github.usernamec446.winterfront.storm.StormProfiles;

/**
 * Shared rules for whether a location takes part in winter. The server decides from its config.
 * Clients do not have that config, so they follow the climate they received: the server's cold
 * biomes, or the seasonal temperature when a seasons mod is installed.
 */
public final class WeatherHelper
{
    private WeatherHelper() {}

    public static boolean isWinterDimension(Level level)
    {
        return level != null && (level.isClientSide || StormProfiles.forDimension(level.dimension()) != null);
    }

    public static boolean supportsWinterWeather(Level level)
    {
        return isWinterDimension(level) && level.dimensionType().hasSkyLight();
    }

    /**
     * Secondary dimensions with {@link DerivedLevelData} share the weather and game rules of the
     * primary level, and their weather setters do nothing.
     */
    public static boolean canControlWeather(Level level)
    {
        return level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && supportsWinterWeather(level)
                && !(serverLevel.getLevelData() instanceof DerivedLevelData);
    }

    /** Whether winter effects apply now. Also called from worldgen threads, so it reads no saved data. */
    public static boolean isWinterActive(Level level)
    {
        if (!supportsWinterWeather(level))
        {
            return false;
        }
        if (level.isClientSide)
        {
            return true;
        }
        final StormProfile profile = StormProfiles.forDimension(level.dimension());
        return level.getDayTime() / 24000L >= profile.firstStormDay()
                && (!ClimateConfig.get().followsSeasons() || profile.seasons().winterSeasons().contains(SeasonCompat.currentSeason(level)));
    }

    /**
     * Static winter makes biomes cold from the first day, so vanilla would snow and freeze too
     * early. Returns whether vanilla's cold-weather effects must be held back at this position.
     */
    public static boolean holdsBackColdEffects(LevelReader reader, Level level, BlockPos pos)
    {
        if (level.isClientSide || !ClimateConfig.get().rewritesClimate() || !isWinterBiome(level, pos))
        {
            return false;
        }
        return !isWinterActive(level) || (!(reader instanceof Level) && !Config.INSTANCE.worldgenFreezeTopLayer.getAsBoolean());
    }

    /** Whether snow may settle here according to the dimension's snow rules. */
    public static boolean allowsSnowSettling(Level level, BlockPos pos)
    {
        final StormProfile profile = level.isClientSide ? null : StormProfiles.forDimension(level.dimension());
        if (profile == null)
        {
            return true;
        }
        final var rule = profile.biomeRule(level.getBiome(pos));
        return rule.flatMap(SnowSettings.BiomeRule::accumulation).orElse(profile.snow().accumulation())
                && pos.getY() >= profile.snow().altitude().minY()
                && level.getBrightness(LightLayer.BLOCK, pos) <= profile.snow().maxBlockLight();
    }

    public static boolean isWinterBiome(Level level, BlockPos pos)
    {
        if (level == null || pos == null || !isWinterDimension(level))
        {
            return false;
        }
        final Holder<Biome> biome = level.getBiome(pos);
        return level.isClientSide
                ? biome.value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW
                : ClimateConfig.get().isWinterBiome(biome);
    }

    public static boolean isActiveWinterBiome(Level level, BlockPos pos)
    {
        return isWinterActive(level) && pos != null && isWinterBiome(level, pos);
    }

    /** Resolves the owning level for a normal level or a world-generation view. */
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

    /** Whether snow is falling at the position right now. */
    public static boolean isSnowingAt(Level level, BlockPos pos)
    {
        if (!isActiveWinterBiome(level, pos) || !level.isRaining() || !level.canSeeSky(pos))
        {
            return false;
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY()
                && (level.isClientSide || SeasonCompat.isColdEnoughToSnow(level, pos));
    }
}
