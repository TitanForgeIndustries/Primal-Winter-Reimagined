package com.alcatrazescapee.primalwinter.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.alcatrazescapee.primalwinter.util.WeatherHelper;

/** Keeps Particle Rain's optional custom precipitation query on the seasonal overlay. */
@Pseudo
@Mixin(targets = "pigcart.particlerain.VersionUtil", remap = false)
public abstract class ParticleRainVersionUtilMixin
{
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void primalwinter$seasonalPrecipitation(Level level, Holder<Biome> biome, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir)
    {
        if (WeatherHelper.isPrimalWinterBiome(level, pos))
        {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}
