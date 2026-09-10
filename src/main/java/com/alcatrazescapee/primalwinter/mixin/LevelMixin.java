package com.alcatrazescapee.primalwinter.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.alcatrazescapee.primalwinter.util.WeatherHelper;

/** Supplies seasonal precipitation to Level's context-aware rain query. */
@Mixin(Level.class)
public abstract class LevelMixin
{
    @Redirect(
            method = "isRainingAt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation primalwinter$seasonalPrecipitation(Biome biome, BlockPos pos)
    {
        return WeatherHelper.getPrecipitation((Level) (Object) this, biome, pos);
    }
}
