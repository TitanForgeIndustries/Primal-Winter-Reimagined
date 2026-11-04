package io.github.usernamec446.winterfront;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * Keeps worlds created with Primal Winter or Primal Winter: Reimagined loadable: every
 * {@code primalwinter:snowy_x} block and item becomes the vanilla {@code minecraft:x}.
 */
public final class LegacyPrimalWinterMappings
{
    public static final String LEGACY_MOD_ID = "primalwinter";
    private static final String SNOWY_PREFIX = "snowy_";

    public static void remapMissing(MissingMappingsEvent event)
    {
        remap(event, ForgeRegistries.Keys.BLOCKS, ForgeRegistries.BLOCKS);
        remap(event, ForgeRegistries.Keys.ITEMS, ForgeRegistries.ITEMS);
        event.getMappings(ForgeRegistries.Keys.SOUND_EVENTS, LEGACY_MOD_ID).forEach(MissingMappingsEvent.Mapping::ignore);
    }

    private static <T> void remap(MissingMappingsEvent event, ResourceKey<? extends net.minecraft.core.Registry<T>> key, IForgeRegistry<T> registry)
    {
        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, LEGACY_MOD_ID))
        {
            final String path = mapping.getKey().getPath();
            final ResourceLocation target = new ResourceLocation("minecraft", path.startsWith(SNOWY_PREFIX) ? path.substring(SNOWY_PREFIX.length()) : path);
            if (registry.containsKey(target))
            {
                mapping.remap(registry.getValue(target));
            }
            else
            {
                mapping.warn();
            }
        }
    }
}
