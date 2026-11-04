package io.github.usernamec446.winterfront.storm;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** Weather schedule for one or more dimensions, loaded from config/winterfront/profiles/*.json. */
public record StormProfile(List<ResourceLocation> dimensions, Mode mode, int firstStormDay, DayRange clearDays, boolean sleepSkipsStorm, List<StormType> stormTypes)
{
    public enum Mode
    {
        /** Clear spells and random storms alternate. */
        CYCLE,
        /** The first storm never ends. */
        ETERNAL,
        /** Vanilla controls the weather; only the climate and snow rules apply. */
        VANILLA;

        static final Codec<Mode> CODEC = Codec.STRING.comapFlatMap(Mode::parse, mode -> mode.name().toLowerCase(Locale.ROOT));

        private static DataResult<Mode> parse(String name)
        {
            for (Mode mode : values())
            {
                if (mode.name().equalsIgnoreCase(name))
                {
                    return DataResult.success(mode);
                }
            }
            return DataResult.error(() -> "Unknown mode '" + name + "', expected cycle, eternal or vanilla");
        }
    }

    private static final Codec<StormProfile> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.listOf().fieldOf("dimensions").forGetter(StormProfile::dimensions),
        Mode.CODEC.optionalFieldOf("mode", Mode.CYCLE).forGetter(StormProfile::mode),
        Codec.INT.optionalFieldOf("firstStormDay", 5).forGetter(StormProfile::firstStormDay),
        DayRange.CODEC.optionalFieldOf("clearDays", new DayRange(2, 6)).forGetter(StormProfile::clearDays),
        Codec.BOOL.optionalFieldOf("sleepSkipsStorm", true).forGetter(StormProfile::sleepSkipsStorm),
        StormType.CODEC.listOf().optionalFieldOf("stormTypes", List.of()).forGetter(StormProfile::stormTypes)
    ).apply(instance, StormProfile::new));

    public static final Codec<StormProfile> CODEC = BASE_CODEC.flatXmap(StormProfile::validate, DataResult::success);

    public static final StormProfile DEFAULT = new StormProfile(
        List.of(new ResourceLocation("minecraft", "overworld")),
        Mode.CYCLE,
        5,
        new DayRange(2, 6),
        true,
        List.of(
            new StormType("light_snow", 60, new DayRange(0.5, 1), false, 0.4f, 0.5f),
            new StormType("snowfall", 30, new DayRange(0.5, 1.5), false, 1f, 1f),
            new StormType("blizzard", 10, new DayRange(0.5, 1), true, 1f, 2f)
        )
    );

    private static DataResult<StormProfile> validate(StormProfile profile)
    {
        if (profile.dimensions.isEmpty())
        {
            return DataResult.error(() -> "A profile needs at least one dimension");
        }
        if (profile.firstStormDay < 0)
        {
            return DataResult.error(() -> "firstStormDay cannot be negative");
        }
        if (profile.mode != Mode.VANILLA && profile.stormTypes.isEmpty())
        {
            return DataResult.error(() -> "Mode " + profile.mode + " needs at least one storm type");
        }
        final HashSet<String> ids = new HashSet<>();
        for (StormType type : profile.stormTypes)
        {
            if (!ids.add(type.id()))
            {
                return DataResult.error(() -> "Duplicate storm type '" + type.id() + "'");
            }
        }
        return DataResult.success(profile);
    }

    public Optional<StormType> stormType(String id)
    {
        return stormTypes.stream().filter(type -> type.id().equals(id)).findFirst();
    }

    public StormType randomStormType(RandomSource random)
    {
        int roll = random.nextInt(stormTypes.stream().mapToInt(StormType::weight).sum());
        for (StormType type : stormTypes)
        {
            roll -= type.weight();
            if (roll < 0)
            {
                return type;
            }
        }
        throw new IllegalStateException("Weights changed during selection");
    }
}
