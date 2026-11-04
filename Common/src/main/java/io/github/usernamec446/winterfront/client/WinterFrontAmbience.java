package io.github.usernamec446.winterfront.client;

import net.minecraft.sounds.SoundEvent;

import io.github.usernamec446.winterfront.util.Helpers;

/**
 * Client-only ambience. The sound event is intentionally not registered: registered sound events
 * are synchronized by Forge and would make the mod mandatory on clients.
 */
public final class WinterFrontAmbience
{
    public static final SoundEvent WIND = SoundEvent.createVariableRangeEvent(Helpers.identifier("wind"));
}
