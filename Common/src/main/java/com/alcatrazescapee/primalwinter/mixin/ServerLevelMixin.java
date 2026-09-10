package com.alcatrazescapee.primalwinter.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.alcatrazescapee.primalwinter.util.EventHandler;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{
    @Redirect(
            method = "tickChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation primalwinter$seasonalPrecipitation(Biome biome, net.minecraft.core.BlockPos pos)
    {
        return com.alcatrazescapee.primalwinter.util.WeatherHelper.getPrecipitation((ServerLevel) (Object) this, biome, pos);
    }

    @Inject(method = "tickChunk", at = @At(value = "RETURN"))
    public void placeExtraSnow(LevelChunk chunk, int tickSpeed, CallbackInfo ci)
    {
        EventHandler.placeExtraSnow((ServerLevel) (Object) this, chunk);
    }
}
