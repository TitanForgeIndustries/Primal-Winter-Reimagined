package io.github.usernamec446.winterfront.mixin;

import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Biome.class)
public interface BiomeAccessor
{
    @Mutable
    @Accessor("climateSettings")
    void winterfront$setClimateSettings(Biome.ClimateSettings climateSettings);
}
