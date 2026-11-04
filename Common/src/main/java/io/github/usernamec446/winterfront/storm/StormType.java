package io.github.usernamec446.winterfront.storm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One kind of storm.
 *
 * @param intensity Maximum rain level sent to clients. Vanilla stops treating the level as raining
 *                  at 0.2, and stops treating it as thundering below 0.9.
 */
public record StormType(String id, int weight, DayRange days, boolean thunder, float intensity, float accumulationMultiplier)
{
    public static final float MIN_INTENSITY = 0.25f;

    private static final Codec<StormType> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(StormType::id),
        Codec.INT.fieldOf("weight").forGetter(StormType::weight),
        DayRange.CODEC.fieldOf("days").forGetter(StormType::days),
        Codec.BOOL.optionalFieldOf("thunder", false).forGetter(StormType::thunder),
        Codec.FLOAT.optionalFieldOf("intensity", 1f).forGetter(StormType::intensity),
        Codec.FLOAT.optionalFieldOf("accumulationMultiplier", 1f).forGetter(StormType::accumulationMultiplier)
    ).apply(instance, StormType::new));

    public static final Codec<StormType> CODEC = BASE_CODEC.flatXmap(StormType::validate, DataResult::success);

    private static DataResult<StormType> validate(StormType type)
    {
        if (type.weight <= 0)
        {
            return DataResult.error(() -> "Storm '" + type.id + "': weight must be positive");
        }
        if (type.intensity < MIN_INTENSITY || type.intensity > 1f)
        {
            return DataResult.error(() -> "Storm '" + type.id + "': intensity must be in [" + MIN_INTENSITY + ", 1]");
        }
        if (type.thunder && type.intensity < 0.9f)
        {
            return DataResult.error(() -> "Storm '" + type.id + "': thunder needs intensity of at least 0.9");
        }
        if (type.accumulationMultiplier < 0f)
        {
            return DataResult.error(() -> "Storm '" + type.id + "': accumulationMultiplier cannot be negative");
        }
        return DataResult.success(type);
    }
}
