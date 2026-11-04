package io.github.usernamec446.winterfront.climate;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.util.StrictCodecs;
import io.github.usernamec446.winterfront.WinterFront;
import io.github.usernamec446.winterfront.platform.XPlatform;

/**
 * Biome climate, from config/winterfront/climate.json. It is baked into biomes once at server
 * start and sent to clients when they log in, so it is loaded only when the game starts.
 */
public record ClimateConfig(Mode mode, TemperatureMode temperatureMode, float temperature, boolean forcePrecipitation, BiomeFilter excludedBiomes, List<Override> biomeOverrides)
{
    public enum Mode
    {
        /** Static winter, until a seasons mod takes over. */
        AUTO,
        /** Static winter, always. */
        STATIC,
        /** Biome climate is never rewritten. */
        OFF;

        static final Codec<Mode> CODEC = enumCodec(values());
    }

    public enum TemperatureMode
    {
        /** Every biome gets exactly this temperature. */
        SET,
        /** The value is added to each biome's temperature. */
        OFFSET,
        /** Biomes warmer than the value are lowered to it. */
        CAP;

        static final Codec<TemperatureMode> CODEC = enumCodec(values());
    }

    /** Biomes matching this entry get exactly this temperature, ignoring the temperature mode. */
    public record Override(BiomeFilter biomes, float temperature)
    {
        static final Codec<Override> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeFilter.CODEC.fieldOf("biomes").forGetter(Override::biomes),
            Codec.FLOAT.fieldOf("temperature").forGetter(Override::temperature)
        ).apply(instance, Override::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    static final Codec<ClimateConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        StrictCodecs.optional(Mode.CODEC, "mode", Mode.AUTO).forGetter(ClimateConfig::mode),
        StrictCodecs.optional(TemperatureMode.CODEC, "temperatureMode", TemperatureMode.SET).forGetter(ClimateConfig::temperatureMode),
        StrictCodecs.optional(Codec.FLOAT, "temperature", -0.5f).forGetter(ClimateConfig::temperature),
        StrictCodecs.optional(Codec.BOOL, "forcePrecipitation", true).forGetter(ClimateConfig::forcePrecipitation),
        StrictCodecs.optional(BiomeFilter.CODEC, "excludedBiomes", BiomeFilter.NONE).forGetter(ClimateConfig::excludedBiomes),
        StrictCodecs.optional(Override.CODEC.listOf(), "biomeOverrides", List.of()).forGetter(ClimateConfig::biomeOverrides)
    ).apply(instance, ClimateConfig::new));

    private static final String DEFAULT_JSON = """
        {
          "mode": "auto",
          "temperatureMode": "set",
          "temperature": -0.5,
          "forcePrecipitation": true,
          "excludedBiomes": ["#minecraft:is_nether", "#minecraft:is_end", "minecraft:the_void"],
          "biomeOverrides": []
        }
        """;

    // Read from worldgen threads, so it is only ever replaced as a whole.
    private static volatile ClimateConfig instance = parseDefault();

    public static ClimateConfig get()
    {
        return instance;
    }

    public static void load()
    {
        final Path file = XPlatform.INSTANCE.configDir().resolve(WinterFront.MOD_ID).resolve("climate.json");
        try
        {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file))
            {
                Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
            {
                final DataResult<ClimateConfig> result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                result.error().ifPresent(error -> LOGGER.error("Invalid climate.json, using defaults: {}", error.message()));
                result.result().ifPresent(config -> instance = config);
            }
        }
        catch (IOException | JsonParseException e)
        {
            LOGGER.error("Could not read {}, using defaults", file, e);
        }
    }

    private static ClimateConfig parseDefault()
    {
        return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(DEFAULT_JSON)).getOrThrow(false, LOGGER::error);
    }

    public boolean isWinterBiome(Holder<Biome> biome)
    {
        return !excludedBiomes.matches(biome);
    }

    public boolean rewritesClimate()
    {
        return mode != Mode.OFF;
    }

    public float temperatureFor(Holder<Biome> biome, float base)
    {
        for (Override override : biomeOverrides)
        {
            if (override.biomes.matches(biome))
            {
                return override.temperature;
            }
        }
        return switch (temperatureMode)
        {
            case SET -> temperature;
            case OFFSET -> base + temperature;
            case CAP -> Math.min(base, temperature);
        };
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(E[] values)
    {
        return Codec.STRING.comapFlatMap(name -> {
            for (E value : values)
            {
                if (value.name().equalsIgnoreCase(name))
                {
                    return DataResult.success(value);
                }
            }
            return DataResult.error(() -> "Unknown value '" + name + "'");
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
