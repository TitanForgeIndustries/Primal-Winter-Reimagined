package io.github.usernamec446.winterfront.storm;

import java.util.List;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.usernamec446.winterfront.util.StrictCodecs;
import io.github.usernamec446.winterfront.climate.BiomeFilter;

/** How snow piles up in a dimension. */
public record SnowSettings(boolean accumulation, float chancePerChunkTick, int maxLayers, int maxBlockLight, Altitude altitude)
{
    public static final int MAX_POSSIBLE_LAYERS = 8;
    private static final int BLOCKS_PER_BONUS_LAYER = 16;

    /** Below minY no snow settles; from bonusLayersFromY, each 16 blocks of height allow one more layer. */
    public record Altitude(int minY, int bonusLayersFromY, int maxBonusLayers)
    {
        static final Altitude DEFAULT = new Altitude(-64, 120, 0);
        static final Codec<Altitude> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.optional(Codec.INT, "minY", DEFAULT.minY).forGetter(Altitude::minY),
            StrictCodecs.optional(Codec.INT, "bonusLayersFromY", DEFAULT.bonusLayersFromY).forGetter(Altitude::bonusLayersFromY),
            StrictCodecs.optional(Codec.intRange(0, MAX_POSSIBLE_LAYERS), "maxBonusLayers", DEFAULT.maxBonusLayers).forGetter(Altitude::maxBonusLayers)
        ).apply(instance, Altitude::new));
    }

    /** The first rule whose biomes match applies; absent values keep the dimension's settings. */
    public record BiomeRule(BiomeFilter biomes, Optional<Boolean> accumulation, float accumulationMultiplier, Optional<Integer> maxLayers)
    {
        static final Codec<BiomeRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeFilter.CODEC.fieldOf("biomes").forGetter(BiomeRule::biomes),
            StrictCodecs.optional(Codec.BOOL, "accumulation").forGetter(BiomeRule::accumulation),
            StrictCodecs.optional(Codec.floatRange(0f, Float.MAX_VALUE), "accumulationMultiplier", 1f).forGetter(BiomeRule::accumulationMultiplier),
            StrictCodecs.optional(Codec.intRange(1, MAX_POSSIBLE_LAYERS), "maxLayers").forGetter(BiomeRule::maxLayers)
        ).apply(instance, BiomeRule::new));

        static final Codec<List<BiomeRule>> LIST_CODEC = CODEC.listOf();
    }

    public static final SnowSettings DEFAULT = new SnowSettings(true, 1f / 16f, 5, 9, Altitude.DEFAULT);

    private static final Codec<SnowSettings> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        StrictCodecs.optional(Codec.BOOL, "accumulation", DEFAULT.accumulation).forGetter(SnowSettings::accumulation),
        StrictCodecs.optional(Codec.floatRange(0f, 1f), "chancePerChunkTick", DEFAULT.chancePerChunkTick).forGetter(SnowSettings::chancePerChunkTick),
        StrictCodecs.optional(Codec.intRange(1, MAX_POSSIBLE_LAYERS), "maxLayers", DEFAULT.maxLayers).forGetter(SnowSettings::maxLayers),
        StrictCodecs.optional(Codec.intRange(0, 15), "maxBlockLight", DEFAULT.maxBlockLight).forGetter(SnowSettings::maxBlockLight),
        StrictCodecs.optional(Altitude.CODEC, "altitude", Altitude.DEFAULT).forGetter(SnowSettings::altitude)
    ).apply(instance, SnowSettings::new));

    public static final Codec<SnowSettings> CODEC = BASE_CODEC.flatXmap(DataResult::success, DataResult::success);

    public int layerLimitAt(int y, Optional<BiomeRule> rule)
    {
        final int base = rule.flatMap(BiomeRule::maxLayers).orElse(maxLayers);
        final int bonus = y < altitude.bonusLayersFromY ? 0 : Math.min(altitude.maxBonusLayers, (y - altitude.bonusLayersFromY) / BLOCKS_PER_BONUS_LAYER + 1);
        return Math.min(MAX_POSSIBLE_LAYERS, base + bonus);
    }
}
