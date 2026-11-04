package io.github.usernamec446.winterfront.frost;

import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** A list of IDs and {@code #tags}, as written in frost.json. Unknown IDs simply never match, so other mods stay optional. */
public final class RegistryFilter<T>
{
    public static <T> Codec<RegistryFilter<T>> codec(ResourceKey<? extends Registry<T>> registry)
    {
        return Codec.STRING.listOf().comapFlatMap(entries -> {
            for (String entry : entries)
            {
                if (ResourceLocation.tryParse(entry.startsWith("#") ? entry.substring(1) : entry) == null)
                {
                    return DataResult.error(() -> "Invalid ID or tag '" + entry + "'");
                }
            }
            return DataResult.success(new RegistryFilter<>(registry, entries));
        }, filter -> filter.entries);
    }

    public static <T> RegistryFilter<T> of(ResourceKey<? extends Registry<T>> registry, String... entries)
    {
        return new RegistryFilter<>(registry, List.of(entries));
    }

    private final List<String> entries;
    private final List<ResourceKey<T>> ids = new ArrayList<>();
    private final List<TagKey<T>> tags = new ArrayList<>();

    private RegistryFilter(ResourceKey<? extends Registry<T>> registry, List<String> entries)
    {
        this.entries = entries;
        for (String entry : entries)
        {
            if (entry.startsWith("#"))
            {
                tags.add(TagKey.create(registry, new ResourceLocation(entry.substring(1))));
            }
            else
            {
                ids.add(ResourceKey.create(registry, new ResourceLocation(entry)));
            }
        }
    }

    public boolean matches(Holder<T> holder)
    {
        return ids.stream().anyMatch(holder::is) || tags.stream().anyMatch(holder::is);
    }
}
