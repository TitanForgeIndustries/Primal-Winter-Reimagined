package io.github.usernamec446.winterfront.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.platform.XPlatform;

/**
 * Optional Serene Seasons support, through reflection so the mod has no dependency on it. Season
 * lookups use the level's saved data, so ticks store the result here for worldgen threads to read.
 */
public final class SeasonCompat
{
    public enum Season
    {
        SPRING, SUMMER, AUTUMN, WINTER;

        public static final Codec<Season> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (Season season : values())
            {
                if (season.name().equalsIgnoreCase(name))
                {
                    return DataResult.success(season);
                }
            }
            return DataResult.error(() -> "Unknown season '" + name + "'");
        }, season -> season.name().toLowerCase(Locale.ROOT));
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, Season> CURRENT = new ConcurrentHashMap<>();

    private static final MethodHandle GET_SEASON_STATE;
    private static final MethodHandle GET_SEASON;
    private static final MethodHandle COLD_ENOUGH;

    static
    {
        MethodHandle seasonState = null, season = null, cold = null;
        if (XPlatform.INSTANCE.isModLoaded("sereneseasons"))
        {
            try
            {
                final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                final Class<?> stateClass = Class.forName("sereneseasons.api.season.ISeasonState");
                seasonState = lookup.findStatic(Class.forName("sereneseasons.api.season.SeasonHelper"), "getSeasonState", MethodType.methodType(stateClass, Level.class));
                season = lookup.findVirtual(stateClass, "getSeason", MethodType.methodType(Class.forName("sereneseasons.api.season.Season")));
                cold = lookup.findStatic(Class.forName("sereneseasons.season.SeasonHooks"), "coldEnoughToSnowSeasonal", MethodType.methodType(boolean.class, LevelReader.class, BlockPos.class));
            }
            catch (ReflectiveOperationException e)
            {
                LOGGER.error("Serene Seasons is installed, but this version is not supported. Seasons are ignored.", e);
                seasonState = season = cold = null;
            }
        }
        GET_SEASON_STATE = seasonState;
        GET_SEASON = season;
        COLD_ENOUGH = cold;
    }

    public static boolean isLoaded()
    {
        return GET_SEASON_STATE != null;
    }

    /** Refreshes the stored season. Call from the server thread. */
    public static void update(ServerLevel level)
    {
        if (isLoaded())
        {
            try
            {
                final Object state = GET_SEASON_STATE.invoke((Level) level);
                CURRENT.put(level.dimension(), Season.valueOf(((Enum<?>) GET_SEASON.invoke(state)).name()));
            }
            catch (Throwable e)
            {
                LOGGER.error("Could not read the season", e);
            }
        }
    }

    /** The stored season, or winter until the level has ticked once. Safe on any thread. */
    public static Season currentSeason(Level level)
    {
        return CURRENT.getOrDefault(level.dimension(), Season.WINTER);
    }

    public static boolean isColdEnoughToSnow(Level level, BlockPos pos)
    {
        if (isLoaded())
        {
            try
            {
                return (boolean) COLD_ENOUGH.invoke((LevelReader) level, pos);
            }
            catch (Throwable e)
            {
                LOGGER.error("Could not read the seasonal temperature", e);
            }
        }
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    private SeasonCompat() {}
}
