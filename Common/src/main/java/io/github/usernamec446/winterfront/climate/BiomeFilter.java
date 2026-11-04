package io.github.usernamec446.winterfront.climate;

import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** A list of biome IDs and {@code #tags}, as written in the JSON config files. */
public final class BiomeFilter
{
    public static final BiomeFilter NONE = new BiomeFilter(List.of());
    public static final Codec<BiomeFilter> CODEC = Codec.STRING.listOf().comapFlatMap(BiomeFilter::parse, filter -> filter.entries);

    private static DataResult<BiomeFilter> parse(List<String> entries)
    {
        for (String entry : entries)
        {
            if (ResourceLocation.tryParse(entry.startsWith("#") ? entry.substring(1) : entry) == null)
            {
                return DataResult.error(() -> "Invalid biome or tag '" + entry + "'");
            }
        }
        return DataResult.success(new BiomeFilter(entries));
    }

    private final List<String> entries;
    private final List<ResourceKey<Biome>> ids = new ArrayList<>();
    private final List<TagKey<Biome>> tags = new ArrayList<>();

    private BiomeFilter(List<String> entries)
    {
        this.entries = entries;
        for (String entry : entries)
        {
            if (entry.startsWith("#"))
            {
                tags.add(TagKey.create(Registries.BIOME, new ResourceLocation(entry.substring(1))));
            }
            else
            {
                ids.add(ResourceKey.create(Registries.BIOME, new ResourceLocation(entry)));
            }
        }
    }

    public boolean matches(Holder<Biome> biome)
    {
        return ids.stream().anyMatch(biome::is) || tags.stream().anyMatch(biome::is);
    }
}
