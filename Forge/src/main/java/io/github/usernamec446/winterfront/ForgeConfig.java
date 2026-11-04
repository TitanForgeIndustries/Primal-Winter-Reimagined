package io.github.usernamec446.winterfront;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import io.github.usernamec446.winterfront.util.Config;

/** Owns config/winterfront/server.toml and client.toml and copies their values into {@link Config}. */
public final class ForgeConfig
{
    public static final String SERVER_FILE = WinterFront.MOD_ID + "/server.toml";
    public static final String CLIENT_FILE = WinterFront.MOD_ID + "/client.toml";

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLE_WEATHER_COMMAND;
    private static final ForgeConfigSpec.BooleanValue FREEZE_TOP_LAYER;
    private static final ForgeConfigSpec.BooleanValue DEEP_SNOW;
    private static final ForgeConfigSpec.BooleanValue ICE_SPIKES;
    private static final ForgeConfigSpec.BooleanValue ICE_PATCHES;
    private static final ForgeConfigSpec.BooleanValue SNOW_PATCHES;
    private static final ForgeConfigSpec.BooleanValue POWDER_SNOW_PATCHES;

    private static final ForgeConfigSpec.DoubleValue FOG_DENSITY;
    private static final ForgeConfigSpec.IntValue SNOW_DENSITY;
    private static final ForgeConfigSpec.BooleanValue WIND_SOUNDS;
    private static final ForgeConfigSpec.BooleanValue SNOW_SOUNDS;
    private static final ForgeConfigSpec.IntValue FOG_COLOR_DAY;
    private static final ForgeConfigSpec.IntValue FOG_COLOR_NIGHT;
    private static final ForgeConfigSpec.BooleanValue WEATHER_RENDER_CHANGES;
    private static final ForgeConfigSpec.BooleanValue SKY_RENDER_CHANGES;

    static
    {
        final ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        ENABLE_WEATHER_COMMAND = server
            .comment("If false, disables the vanilla /weather command.")
            .define("enableWeatherCommand", true);
        server.comment("World generation. These only affect newly generated chunks and need a server restart.").push("worldgen");
        FREEZE_TOP_LAYER = server
            .comment("Cover new chunks in snow and freeze their water once winter has started.")
            .define("freezeTopLayer", true);
        DEEP_SNOW = server
            .comment("Generate snow more than one layer deep. Deep snow prevents most surface mob spawning.")
            .define("deepSnow", false);
        ICE_SPIKES = server.define("iceSpikes", true);
        ICE_PATCHES = server.define("icePatches", true);
        SNOW_PATCHES = server.define("snowPatches", true);
        POWDER_SNOW_PATCHES = server.define("powderSnowPatches", true);
        server.pop();
        SERVER_SPEC = server.build();

        final ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        FOG_DENSITY = client
            .comment("How dense the fog effect during a snowstorm is.")
            .defineInRange("fogDensity", 0.5D, 0.0D, 5.0D);
        SNOW_DENSITY = client
            .comment("How visually dense the snow weather effect is. Vanilla uses 5 (fast) or 10 (fancy); 15 is the maximum.")
            .defineInRange("snowDensity", 15, 1, 15);
        WIND_SOUNDS = client
            .comment("Enable additional wind / snowstorm ambience.")
            .define("windSounds", true);
        SNOW_SOUNDS = client
            .comment("Enable additional snow (rain) weather sounds.")
            .define("snowSounds", true);
        FOG_COLOR_DAY = client
            .comment("Fog color during the day, as a decimal 0xRRGGBB value. Default is 0xBFBFD8 (12566488).")
            .defineInRange("fogColorDay", 0xBFBFD8, 0, 0xFFFFFF);
        FOG_COLOR_NIGHT = client
            .comment("Fog color during the night, as a decimal 0xRRGGBB value. Default is 0x0C0C19 (789529).")
            .defineInRange("fogColorNight", 0x0C0C19, 0, 0xFFFFFF);
        WEATHER_RENDER_CHANGES = client
            .comment("Replace the vanilla rain renderer with one that renders faster, denser snow.")
            .define("weatherRenderChanges", true);
        SKY_RENDER_CHANGES = client
            .comment("Disable sunrise / sunset sky effects during a snowstorm for a bleak, overcast look.")
            .define("skyRenderChanges", true);
        CLIENT_SPEC = client.build();
    }

    public static void onConfigEvent(ModConfigEvent event)
    {
        final ModConfig config = event.getConfig();
        if (config.getSpec() == SERVER_SPEC)
        {
            bakeServer();
        }
        else if (config.getSpec() == CLIENT_SPEC)
        {
            bakeClient();
        }
    }

    private static void bakeServer()
    {
        final Config config = Config.INSTANCE;
        config.enableWeatherCommand.set(ENABLE_WEATHER_COMMAND.get());
        config.worldgenFreezeTopLayer.set(FREEZE_TOP_LAYER.get());
        config.worldgenDeepSnow.set(DEEP_SNOW.get());
        config.worldgenIceSpikes.set(ICE_SPIKES.get());
        config.worldgenIcePatches.set(ICE_PATCHES.get());
        config.worldgenSnowPatches.set(SNOW_PATCHES.get());
        config.worldgenPowderSnowPatches.set(POWDER_SNOW_PATCHES.get());
    }

    private static void bakeClient()
    {
        final Config config = Config.INSTANCE;
        config.fogDensity.set(FOG_DENSITY.get().floatValue());
        config.snowDensity.set(SNOW_DENSITY.get());
        config.windSounds.set(WIND_SOUNDS.get());
        config.snowSounds.set(SNOW_SOUNDS.get());
        config.fogColorDay.set(FOG_COLOR_DAY.get());
        config.fogColorNight.set(FOG_COLOR_NIGHT.get());
        config.weatherRenderChanges.set(WEATHER_RENDER_CHANGES.get());
        config.skyRenderChanges.set(SKY_RENDER_CHANGES.get());
    }

    private ForgeConfig() {}
}
