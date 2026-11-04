package io.github.usernamec446.winterfront.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.usernamec446.winterfront.util.WeatherHelper;

/**
 * Biomes are cold on their own, so vanilla places the snow and ice. This only applies the
 * config's limits, and otherwise leaves the result to vanilla or a seasons mod.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin
{
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void winterfront$shouldFreeze(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && WeatherHelper.holdsBackColdEffects(reader, level, pos))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
    private void winterfront$shouldFreezeWithNeighbourCheck(LevelReader reader, BlockPos pos, boolean requireNeighbours, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && WeatherHelper.holdsBackColdEffects(reader, level, pos))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void winterfront$shouldSnow(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final Level level = WeatherHelper.seasonalLevel(reader);
        if (level != null && (WeatherHelper.holdsBackColdEffects(reader, level, pos)
                || (reader instanceof Level && !WeatherHelper.allowsSnowSettling(level, pos))))
        {
            cir.setReturnValue(false);
        }
    }
}
