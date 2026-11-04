package io.github.usernamec446.winterfront.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.usernamec446.winterfront.storm.StormManager;
import io.github.usernamec446.winterfront.util.EventHandler;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin
{
    /** Ordinal 0 clamps the thunder level; ordinal 1 clamps the rain level. */
    @ModifyArg(
            method = "advanceWeatherCycle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 1),
            index = 2)
    private float winterfront$capRainLevel(float max)
    {
        return StormManager.rainLevelCap((ServerLevel) (Object) this);
    }

    @Inject(method = "tickChunk", at = @At(value = "RETURN"))
    public void placeExtraSnow(LevelChunk chunk, int tickSpeed, CallbackInfo ci)
    {
        EventHandler.placeExtraSnow((ServerLevel) (Object) this, chunk);
    }
}
