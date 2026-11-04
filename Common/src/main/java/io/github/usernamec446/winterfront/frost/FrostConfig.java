package io.github.usernamec446.winterfront.frost;

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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.WinterFront;
import io.github.usernamec446.winterfront.platform.XPlatform;
import io.github.usernamec446.winterfront.util.StrictCodecs;

/**
 * Optional effect of storms on players, from config/winterfront/frost.json. It is off by default,
 * since many packs already have temperature mods and their own warm clothing.
 */
public record FrostConfig(boolean enabled, Mode mode, float secondsToFreeze, float maxFreeze, boolean skyExposureRequired, List<String> externalTemperatureMods, Protection protection)
{
    public enum Mode
    {
        /** Frost overlay and vanilla's slowdown, never reaching the freezing damage. */
        SLOW_ONLY,
        /** Keeps freezing up to vanilla's freezing damage. */
        DAMAGE;

        static final Codec<Mode> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (Mode mode : values())
            {
                if (mode.name().equalsIgnoreCase(name))
                {
                    return DataResult.success(mode);
                }
            }
            return DataResult.error(() -> "Unknown frost mode '" + name + "', expected slow_only or damage");
        }, mode -> mode.name().toLowerCase(Locale.ROOT));
    }

    public record Protection(RegistryFilter<Item> armor, int piecesRequired, RegistryFilter<Enchantment> enchantments, RegistryFilter<MobEffect> effects, RegistryFilter<Block> heatBlocks, int heatRadius)
    {
        static final Protection DEFAULT = new Protection(
            RegistryFilter.of(Registries.ITEM, "#minecraft:freeze_immune_wearables", "#winterfront:frost_protection"), 1,
            RegistryFilter.of(Registries.ENCHANTMENT),
            RegistryFilter.of(Registries.MOB_EFFECT, "minecraft:fire_resistance"),
            RegistryFilter.of(Registries.BLOCK, "#minecraft:campfires", "minecraft:lava", "minecraft:fire", "minecraft:soul_fire"), 6
        );

        static final Codec<Protection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.optional(RegistryFilter.codec(Registries.ITEM), "armor", DEFAULT.armor).forGetter(Protection::armor),
            StrictCodecs.optional(Codec.intRange(1, 4), "piecesRequired", DEFAULT.piecesRequired).forGetter(Protection::piecesRequired),
            StrictCodecs.optional(RegistryFilter.codec(Registries.ENCHANTMENT), "enchantments", DEFAULT.enchantments).forGetter(Protection::enchantments),
            StrictCodecs.optional(RegistryFilter.codec(Registries.MOB_EFFECT), "effects", DEFAULT.effects).forGetter(Protection::effects),
            StrictCodecs.optional(RegistryFilter.codec(Registries.BLOCK), "heatBlocks", DEFAULT.heatBlocks).forGetter(Protection::heatBlocks),
            StrictCodecs.optional(Codec.intRange(0, 16), "heatRadius", DEFAULT.heatRadius).forGetter(Protection::heatRadius)
        ).apply(instance, Protection::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    static final FrostConfig DEFAULT = new FrostConfig(false, Mode.SLOW_ONLY, 60f, 0.5f, true, List.of("toughasnails", "cold_sweat"), Protection.DEFAULT);

    private static final Codec<FrostConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        StrictCodecs.optional(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(FrostConfig::enabled),
        StrictCodecs.optional(Mode.CODEC, "mode", DEFAULT.mode).forGetter(FrostConfig::mode),
        StrictCodecs.optional(Codec.floatRange(1f, 86400f), "secondsToFreeze", DEFAULT.secondsToFreeze).forGetter(FrostConfig::secondsToFreeze),
        StrictCodecs.optional(Codec.floatRange(0f, 0.99f), "maxFreeze", DEFAULT.maxFreeze).forGetter(FrostConfig::maxFreeze),
        StrictCodecs.optional(Codec.BOOL, "skyExposureRequired", DEFAULT.skyExposureRequired).forGetter(FrostConfig::skyExposureRequired),
        StrictCodecs.optional(Codec.STRING.listOf(), "externalTemperatureMods", DEFAULT.externalTemperatureMods).forGetter(FrostConfig::externalTemperatureMods),
        StrictCodecs.optional(Protection.CODEC, "protection", DEFAULT.protection).forGetter(FrostConfig::protection)
    ).apply(instance, FrostConfig::new));

    private static final String DEFAULT_JSON = """
        {
          "enabled": false,
          "mode": "slow_only",
          "secondsToFreeze": 60.0,
          "maxFreeze": 0.5,
          "skyExposureRequired": true,
          "externalTemperatureMods": ["toughasnails", "cold_sweat"],
          "protection": {
            "armor": ["#minecraft:freeze_immune_wearables", "#winterfront:frost_protection"],
            "piecesRequired": 1,
            "enchantments": [],
            "effects": ["minecraft:fire_resistance"],
            "heatBlocks": ["#minecraft:campfires", "minecraft:lava", "minecraft:fire", "minecraft:soul_fire"],
            "heatRadius": 6
          }
        }
        """;

    private static volatile FrostConfig instance = DEFAULT;

    public static FrostConfig get()
    {
        return instance;
    }

    /** @return true if the file was valid. A broken file keeps the current settings. */
    public static boolean load()
    {
        final Path file = XPlatform.INSTANCE.configDir().resolve(WinterFront.MOD_ID).resolve("frost.json");
        try
        {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file))
            {
                Files.writeString(file, DEFAULT_JSON, StandardCharsets.UTF_8);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
            {
                final DataResult<FrostConfig> result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                result.error().ifPresent(error -> LOGGER.error("Invalid frost.json, settings not changed: {}", error.message()));
                result.result().ifPresent(config -> instance = config);
                return result.result().isPresent();
            }
        }
        catch (IOException | JsonParseException e)
        {
            LOGGER.error("Could not read {}, settings not changed", file, e);
            return false;
        }
    }
}
