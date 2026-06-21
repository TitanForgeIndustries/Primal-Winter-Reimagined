package com.alcatrazescapee.primalwinter;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

import com.alcatrazescapee.primalwinter.util.Config;

/**
 * Forge-side config definition for Primal Winter: Reimagined.
 *
 * <p>This builds the {@link ForgeConfigSpec} that Forge serialises to
 * {@code config/primalwinter-common.toml}, and {@link #bake()} copies the parsed values into the
 * loader-agnostic {@link Config} holders. {@code bake} is wired into {@link Config#onLoad} during
 * mod construction, and runs both on initial load and whenever {@code /primalwinterReloadConfig}
 * calls {@link Config#load()}.</p>
 */
public final class ForgeConfig
{
    public static final ForgeConfigSpec SPEC;

    // General
    private static final ForgeConfigSpec.BooleanValue ENABLE_WEATHER_COMMAND;
    private static final ForgeConfigSpec.BooleanValue ENABLE_SNOW_ACCUMULATION_WORLDGEN;
    private static final ForgeConfigSpec.BooleanValue ENABLE_SNOW_ACCUMULATION_WEATHER;
    private static final ForgeConfigSpec.BooleanValue INVERT_NON_WINTER_BIOMES;
    private static final ForgeConfigSpec.BooleanValue INVERT_NON_WINTER_DIMENSIONS;
    private static final ForgeConfigSpec.IntValue SNOW_START_DAY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NON_WINTER_DIMENSIONS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NON_WINTER_BIOMES;

    // Client
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
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("General settings for Primal Winter: Reimagined").push("general");

        ENABLE_WEATHER_COMMAND = builder
                .comment("If false, disables the vanilla /weather command.")
                .define("enableWeatherCommand", false);

        ENABLE_SNOW_ACCUMULATION_WORLDGEN = builder
                .comment(
                        "If true, snow will be layered higher than one layer during world generation.",
                        "Note: snow layers > 1 block tall tend to prevent most surface mob spawning, since there are no flat places to spawn."
                )
                .define("enableSnowAccumulationDuringWorldgen", false);

        ENABLE_SNOW_ACCUMULATION_WEATHER = builder
                .comment("If true, snow will gradually stack higher than one layer during active weather, once the storm has begun.")
                .define("enableSnowAccumulationDuringWeather", true);

        INVERT_NON_WINTER_BIOMES = builder
                .comment("If true, the built-in non-winter biome list is treated as the ONLY winter biomes, and all others are left alone.")
                .define("invertNonWinterBiomes", false);

        INVERT_NON_WINTER_DIMENSIONS = builder
                .comment("If true, the built-in non-winter dimension list is treated as the ONLY winter dimensions, and all others are left alone.")
                .define("invertNonWinterDimensions", false);

        SNOW_START_DAY = builder
                .comment("The in-game day on which the permanent snowstorm begins. Before this day, the skies are kept clear so you can gear up.")
                .defineInRange("snowStartDay", 5, 0, Integer.MAX_VALUE);

        NON_WINTER_DIMENSIONS = builder
                .comment(
                        "Dimensions that should NOT be frozen by Primal Winter.",
                        "Add modded dimension IDs here to leave them alone, e.g. \"aether:the_aether\", \"twilightforest:twilight_forest\".",
                        "If invertNonWinterDimensions is true, this instead becomes the ONLY list of dimensions that ARE frozen."
                )
                .defineList("nonWinterDimensions",
                        List.of("minecraft:the_nether", "minecraft:the_end"),
                        o -> o instanceof String);

        NON_WINTER_BIOMES = builder
                .comment(
                        "Biomes that should NOT be frozen by Primal Winter.",
                        "Add modded biome IDs here to leave them alone.",
                        "If invertNonWinterBiomes is true, this instead becomes the ONLY list of biomes that ARE frozen.",
                        "NOTE: changes to this list only apply on world (re)load, not via a live /reload."
                )
                .defineList("nonWinterBiomes",
                        List.of(
                                "minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest",
                                "minecraft:basalt_deltas", "minecraft:soul_sand_valley", "minecraft:end_barrens",
                                "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:the_end", "minecraft:the_void"
                        ),
                        o -> o instanceof String);

        builder.pop();

        builder.comment("Client-side visual and sound settings").push("client");

        FOG_DENSITY = builder
                .comment("How dense the fog effect during a snowstorm is.")
                .defineInRange("fogDensity", 0.5D, 0.0D, 5.0D);

        SNOW_DENSITY = builder
                .comment("How visually dense the snow weather effect is. Vanilla uses 5 (fast) or 10 (fancy); higher fills the sky but costs performance.")
                .defineInRange("snowDensity", 15, 1, 15);

        WIND_SOUNDS = builder
                .comment("Enable additional wind / snowstorm ambience.")
                .define("windSounds", true);

        SNOW_SOUNDS = builder
                .comment("Enable additional snow (rain) weather sounds.")
                .define("snowSounds", true);

        FOG_COLOR_DAY = builder
                .comment("Fog color during the day, as a decimal 0xRRGGBB value. Default is 0xBFBFD8 (12566488).")
                .defineInRange("fogColorDay", 0xBFBFD8, 0, 0xFFFFFF);

        FOG_COLOR_NIGHT = builder
                .comment("Fog color during the night, as a decimal 0xRRGGBB value. Default is 0x0C0C19 (789529).")
                .defineInRange("fogColorNight", 0x0C0C19, 0, 0xFFFFFF);

        WEATHER_RENDER_CHANGES = builder
                .comment("Replace the vanilla rain renderer with one that renders faster, denser snow.")
                .define("weatherRenderChanges", true);

        SKY_RENDER_CHANGES = builder
                .comment("Disable sunrise / sunset sky effects during a snowstorm for a bleak, overcast look.")
                .define("skyRenderChanges", true);

        builder.pop();

        SPEC = builder.build();
    }

    /** Copies parsed config values into the loader-agnostic {@link Config} holders. */
    public static void bake()
    {
        final Config config = Config.INSTANCE;

        config.enableWeatherCommand.set(ENABLE_WEATHER_COMMAND.get());
        config.enableSnowAccumulationDuringWorldgen.set(ENABLE_SNOW_ACCUMULATION_WORLDGEN.get());
        config.enableSnowAccumulationDuringWeather.set(ENABLE_SNOW_ACCUMULATION_WEATHER.get());
        config.invertNonWinterBiomes.set(INVERT_NON_WINTER_BIOMES.get());
        config.invertNonWinterDimensions.set(INVERT_NON_WINTER_DIMENSIONS.get());
        config.snowStartDay.set(SNOW_START_DAY.get());
        config.setNonWinterDimensions(NON_WINTER_DIMENSIONS.get());
        config.setNonWinterBiomes(NON_WINTER_BIOMES.get());

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