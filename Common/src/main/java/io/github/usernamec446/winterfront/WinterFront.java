package io.github.usernamec446.winterfront;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.climate.ClimateConfig;
import io.github.usernamec446.winterfront.frost.FrostConfig;
import io.github.usernamec446.winterfront.storm.StormProfiles;
import io.github.usernamec446.winterfront.world.WinterFrontFeatures;

public final class WinterFront
{
    public static final String MOD_ID = "winterfront";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void earlySetup()
    {
        LOGGER.info("Early XPlatform Setup");

        WinterFrontFeatures.FEATURES.earlySetup();
    }

    public static void lateSetup()
    {
        LOGGER.info("Late XPlatform Setup");

        WinterFrontFeatures.FEATURES.lateSetup();

        ClimateConfig.load();
        StormProfiles.load();
        FrostConfig.load();
    }
}