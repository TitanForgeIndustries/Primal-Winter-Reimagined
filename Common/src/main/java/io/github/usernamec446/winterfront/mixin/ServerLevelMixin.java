package io.github.usernamec446.winterfront.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.usernamec446.winterfront.util.EventHandler;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{
    @Redirect(
            method = "tickChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation winterfront$seasonalPrecipitation(Biome biome, net.minecraft.core.BlockPos pos)
    {
        return io.github.usernamec446.winterfront.util.WeatherHelper.getPrecipitation((ServerLevel) (Object) this, biome, pos);
    }

    @Inject(method = "tickChunk", at = @At(value = "RETURN"))
    public void placeExtraSnow(LevelChunk chunk, int tickSpeed, CallbackInfo ci)
    {
        EventHandler.placeExtraSnow((ServerLevel) (Object) this, chunk);
    }
}
