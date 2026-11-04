package io.github.usernamec446.winterfront;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.util.Config;
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


        Config.INSTANCE.load();
    }
}