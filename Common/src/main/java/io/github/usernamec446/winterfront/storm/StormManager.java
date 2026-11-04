package io.github.usernamec446.winterfront.storm;

import java.util.Optional;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.util.WeatherHelper;

/**
 * Runs the storm schedule. Weather is only written when a phase changes, so /weather stays usable
 * until the next scheduled change.
 */
public final class StormManager
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void tick(ServerLevel level)
    {
        final StormProfile profile = controllingProfile(level);
        if (profile == null)
        {
            return;
        }

        final StormState state = StormState.get(level);
        final GameRules.BooleanValue weatherCycle = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        if (profile.mode() == StormProfile.Mode.VANILLA)
        {
            if (state.managesWeatherCycle)
            {
                state.managesWeatherCycle = false;
                state.phase = StormState.Phase.NONE;
                state.setDirty();
                weatherCycle.set(true, level.getServer());
            }
            return;
        }

        // Vanilla's own cycle would overwrite the schedule.
        if (weatherCycle.get())
        {
            weatherCycle.set(false, level.getServer());
        }
        if (!state.managesWeatherCycle)
        {
            state.managesWeatherCycle = true;
            state.setDirty();
        }

        if (!state.started && level.getDayTime() / DayRange.TICKS_PER_DAY < profile.firstStormDay())
        {
            if (state.phase != StormState.Phase.WAITING)
            {
                enter(level, state, StormState.Phase.WAITING, "", Long.MAX_VALUE, false, false);
            }
            return;
        }

        final long now = level.getGameTime();
        final boolean eternal = profile.mode() == StormProfile.Mode.ETERNAL;
        switch (state.phase)
        {
            case NONE, WAITING -> startStorm(level, profile, state, profile.randomStormType(level.random));
            case CLEAR ->
            {
                if (eternal || now >= state.endTick)
                {
                    startStorm(level, profile, state, profile.randomStormType(level.random));
                }
            }
            case STORM ->
            {
                final Optional<StormType> type = profile.stormType(state.stormId);
                if (type.isEmpty())
                {
                    // The running storm type was removed from the config.
                    startStorm(level, profile, state, profile.randomStormType(level.random));
                }
                else if (!eternal && state.endTick == Long.MAX_VALUE)
                {
                    // The profile changed from eternal to cycle.
                    state.endTick = now + type.get().days().randomTicks(level.random);
                    state.setDirty();
                }
                else if (now >= state.endTick)
                {
                    startClear(level, profile, state);
                }
            }
        }
    }

    /** Ends a running storm when players sleep through it, if the profile allows that. */
    public static void onSleepFinished(ServerLevel level)
    {
        final StormProfile profile = controllingProfile(level);
        if (profile != null && profile.mode() == StormProfile.Mode.CYCLE && profile.sleepSkipsStorm())
        {
            final StormState state = StormState.get(level);
            if (state.phase == StormState.Phase.STORM)
            {
                startClear(level, profile, state);
            }
        }
    }

    /** The upper limit of the rain level for this tick, which sets how dense the snowfall looks. */
    public static float rainLevelCap(ServerLevel level)
    {
        return activeStorm(level).map(StormType::intensity).orElse(1f);
    }

    public static float accumulationMultiplier(ServerLevel level)
    {
        return activeStorm(level).map(StormType::accumulationMultiplier).orElse(1f);
    }

    public static Optional<StormType> activeStorm(ServerLevel level)
    {
        final StormProfile profile = controllingProfile(level);
        if (profile == null || profile.mode() == StormProfile.Mode.VANILLA)
        {
            return Optional.empty();
        }
        final StormState state = StormState.get(level);
        return state.phase == StormState.Phase.STORM ? profile.stormType(state.stormId) : Optional.empty();
    }

    /** Null if this level has no profile, or if it cannot own its weather. */
    public static StormProfile controllingProfile(ServerLevel level)
    {
        return WeatherHelper.canControlWeather(level) ? StormProfiles.forDimension(level.dimension()) : null;
    }

    static void startStorm(ServerLevel level, StormProfile profile, StormState state, StormType type)
    {
        final long end = profile.mode() == StormProfile.Mode.ETERNAL ? Long.MAX_VALUE : level.getGameTime() + type.days().randomTicks(level.random);
        state.started = true;
        enter(level, state, StormState.Phase.STORM, type.id(), end, true, type.thunder());
    }

    static void startClear(ServerLevel level, StormProfile profile, StormState state)
    {
        state.started = true;
        enter(level, state, StormState.Phase.CLEAR, "", level.getGameTime() + profile.clearDays().randomTicks(level.random), false, false);
    }

    private static void enter(ServerLevel level, StormState state, StormState.Phase phase, String stormId, long endTick, boolean raining, boolean thundering)
    {
        state.phase = phase;
        state.stormId = stormId;
        state.endTick = endTick;
        state.setDirty();
        // The vanilla cycle is off, so these durations are never counted down.
        level.setWeatherParameters(raining ? 0 : Integer.MAX_VALUE, raining ? Integer.MAX_VALUE : 0, raining, thundering);
        LOGGER.info("Weather in {}: {} {} until game tick {}", level.dimension().location(), phase, stormId, endTick == Long.MAX_VALUE ? "(no end)" : endTick);
    }

    private StormManager() {}
}
