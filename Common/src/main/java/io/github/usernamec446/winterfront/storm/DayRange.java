package io.github.usernamec446.winterfront.storm;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.RandomSource;

/** A duration range in in-game days, written in JSON as {@code [min, max]}. */
public record DayRange(double min, double max)
{
    public static final int TICKS_PER_DAY = 24000;

    public static final Codec<DayRange> CODEC = Codec.DOUBLE.listOf().comapFlatMap(DayRange::parse, range -> List.of(range.min, range.max));

    private static DataResult<DayRange> parse(List<Double> values)
    {
        if (values.size() != 2)
        {
            return DataResult.error(() -> "A day range must be [min, max], got " + values);
        }
        final double min = values.get(0), max = values.get(1);
        if (min <= 0 || max < min)
        {
            return DataResult.error(() -> "A day range needs 0 < min <= max, got " + values);
        }
        return DataResult.success(new DayRange(min, max));
    }

    public long randomTicks(RandomSource random)
    {
        return Math.max(1L, Math.round((min + random.nextDouble() * (max - min)) * TICKS_PER_DAY));
    }
}
