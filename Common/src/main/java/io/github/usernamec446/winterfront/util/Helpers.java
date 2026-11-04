package io.github.usernamec446.winterfront.util;

import net.minecraft.resources.ResourceLocation;

import io.github.usernamec446.winterfront.WinterFront;

public final class Helpers
{
    public static ResourceLocation identifier(String name)
    {
        return new ResourceLocation(WinterFront.MOD_ID, name);
    }

}
