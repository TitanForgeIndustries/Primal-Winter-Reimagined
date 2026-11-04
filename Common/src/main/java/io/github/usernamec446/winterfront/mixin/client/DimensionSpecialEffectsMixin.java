package io.github.usernamec446.winterfront.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.usernamec446.winterfront.util.Config;
import io.github.usernamec446.winterfront.util.WeatherHelper;

@Mixin(DimensionSpecialEffects.class)
public abstract class DimensionSpecialEffectsMixin
{
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "getSunriseColor", at = @At("RETURN"), cancellable = true)
    private void noSunriseColor(float skyAngle, float tickDelta, CallbackInfoReturnable<float[]> cir)
    {
        final float[] original = cir.getReturnValue();
        final Level level = Minecraft.getInstance().level;
        if (original != null && Config.INSTANCE.skyRenderChanges.getAsBoolean() && level != null)
        {
            final BlockPos pos = Minecraft.getInstance().gameRenderer.getMainCamera().getBlockPosition();
            if (WeatherHelper.isSnowingAt(level, pos))
            {
                cir.setReturnValue(null);
            }
        }
    }
}
