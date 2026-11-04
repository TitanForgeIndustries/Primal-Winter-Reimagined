package io.github.usernamec446.winterfront.storm;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.usernamec446.winterfront.compat.SeasonCompat.Season;
import io.github.usernamec446.winterfront.util.StrictCodecs;

/**
 * Used only with a seasons mod.
 *
 * @param stormChance Chance per season that a storm starts when a clear spell ends.
 * @param winterSeasons Seasons in which winter effects apply: extra snow, new-chunk snow and mobs.
 */
public record SeasonSettings(StormChance stormChance, List<Season> winterSeasons)
{
    public record StormChance(float spring, float summer, float autumn, float winter)
    {
        static final StormChance DEFAULT = new StormChance(0.3f, 0f, 0.5f, 1f);
        private static final Codec<Float> CHANCE = Codec.floatRange(0f, 1f);

        static final Codec<StormChance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.optional(CHANCE, "spring", DEFAULT.spring).forGetter(StormChance::spring),
            StrictCodecs.optional(CHANCE, "summer", DEFAULT.summer).forGetter(StormChance::summer),
            StrictCodecs.optional(CHANCE, "autumn", DEFAULT.autumn).forGetter(StormChance::autumn),
            StrictCodecs.optional(CHANCE, "winter", DEFAULT.winter).forGetter(StormChance::winter)
        ).apply(instance, StormChance::new));

        float of(Season season)
        {
            return switch (season)
            {
                case SPRING -> spring;
                case SUMMER -> summer;
                case AUTUMN -> autumn;
                case WINTER -> winter;
            };
        }
    }

    public static final SeasonSettings DEFAULT = new SeasonSettings(StormChance.DEFAULT, List.of(Season.WINTER));

    public static final Codec<SeasonSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        StrictCodecs.optional(StormChance.CODEC, "stormChance", DEFAULT.stormChance).forGetter(SeasonSettings::stormChance),
        StrictCodecs.optional(Season.CODEC.listOf(), "winterSeasons", DEFAULT.winterSeasons).forGetter(SeasonSettings::winterSeasons)
    ).apply(instance, SeasonSettings::new));

    public float stormChanceIn(Season season)
    {
        return stormChance.of(season);
    }
}
