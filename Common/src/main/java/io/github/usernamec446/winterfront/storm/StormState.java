package io.github.usernamec446.winterfront.storm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world schedule progress, so storms survive server restarts. */
public final class StormState extends SavedData
{
    public enum Phase { NONE, WAITING, CLEAR, STORM }

    private static final String NAME = "winterfront_storm";

    public static StormState get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(StormState::load, StormState::new, NAME);
    }

    private static StormState load(CompoundTag tag)
    {
        final StormState state = new StormState();
        try
        {
            state.phase = Phase.valueOf(tag.getString("phase"));
        }
        catch (IllegalArgumentException e)
        {
            state.phase = Phase.NONE;
        }
        state.stormId = tag.getString("stormId");
        state.endTick = tag.getLong("endTick");
        state.started = tag.getBoolean("started");
        state.managesWeatherCycle = tag.getBoolean("managesWeatherCycle");
        return state;
    }

    Phase phase = Phase.NONE;
    String stormId = "";
    long endTick;
    boolean started;
    boolean managesWeatherCycle;

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putString("phase", phase.name());
        tag.putString("stormId", stormId);
        tag.putLong("endTick", endTick);
        tag.putBoolean("started", started);
        tag.putBoolean("managesWeatherCycle", managesWeatherCycle);
        return tag;
    }
}
