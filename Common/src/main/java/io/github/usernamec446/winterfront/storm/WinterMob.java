package io.github.usernamec446.winterfront.storm;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import io.github.usernamec446.winterfront.util.StrictCodecs;

/** A mob added to the natural spawns of winter biomes. */
public record WinterMob(EntityType<?> entity, MobCategory category, int weight, List<Integer> group, boolean duringStormOnly)
{
    // The registry's own codec turns unknown IDs into its default entry, the pig.
    private static final Codec<EntityType<?>> ENTITY_CODEC = ResourceLocation.CODEC.comapFlatMap(
        id -> BuiltInRegistries.ENTITY_TYPE.containsKey(id)
            ? DataResult.<EntityType<?>>success(BuiltInRegistries.ENTITY_TYPE.get(id))
            : DataResult.error(() -> "Unknown entity '" + id + "'"),
        EntityType::getKey);

    private static final Codec<WinterMob> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENTITY_CODEC.fieldOf("entity").forGetter(WinterMob::entity),
        MobCategory.CODEC.fieldOf("category").forGetter(WinterMob::category),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(WinterMob::weight),
        StrictCodecs.optional(Codec.INT.listOf(), "group", List.of(1, 3)).forGetter(WinterMob::group),
        StrictCodecs.optional(Codec.BOOL, "duringStormOnly", false).forGetter(WinterMob::duringStormOnly)
    ).apply(instance, WinterMob::new));

    public static final Codec<WinterMob> CODEC = BASE_CODEC.flatXmap(WinterMob::validate, DataResult::success);

    private static DataResult<WinterMob> validate(WinterMob mob)
    {
        final List<Integer> group = mob.group;
        if (group.size() != 2 || group.get(0) < 1 || group.get(1) < group.get(0))
        {
            return DataResult.error(() -> "Mob group must be [min, max] with 1 <= min <= max, got " + group);
        }
        return DataResult.success(mob);
    }
}
