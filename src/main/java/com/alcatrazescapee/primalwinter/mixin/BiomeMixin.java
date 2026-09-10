package com.alcatrazescapee.primalwinter.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.alcatrazescapee.primalwinter.util.WeatherHelper;

/** Runtime cold overlay for vanilla's context-bearing biome climate checks. */
@Mixin(Biome.class)
public abstract class BiomeMixin
{
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void primalwinter$shouldFreeze(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && WeatherHelper.isPrimalWinterBiome(level, pos))
        {
            cir.setReturnValue(WeatherHelper.canFreeze(reader, pos, true));
        }
    }

    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
    private void primalwinter$shouldFreezeWithNeighbourCheck(LevelReader reader, BlockPos pos, boolean requireNeighbours, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && WeatherHelper.isPrimalWinterBiome(level, pos))
        {
            cir.setReturnValue(WeatherHelper.canFreeze(reader, pos, requireNeighbours));
        }
    }

    @Inject(method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void primalwinter$shouldSnow(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && WeatherHelper.isPrimalWinterBiome(level, pos))
        {
            cir.setReturnValue(WeatherHelper.canPlaceSnow(reader, pos));
        }
    }
}
