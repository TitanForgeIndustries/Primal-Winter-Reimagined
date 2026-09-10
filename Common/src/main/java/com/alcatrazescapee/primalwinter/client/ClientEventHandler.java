package com.alcatrazescapee.primalwinter.client;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.resources.ResourceKey;

import com.alcatrazescapee.primalwinter.blocks.PrimalWinterBlocks;
import com.alcatrazescapee.primalwinter.platform.client.BlockColorCallback;
import com.alcatrazescapee.primalwinter.platform.client.FogColorCallback;
import com.alcatrazescapee.primalwinter.platform.client.FogDensityCallback;
import com.alcatrazescapee.primalwinter.platform.client.ItemColorCallback;
import com.alcatrazescapee.primalwinter.platform.client.ParticleProviderCallback;
import com.alcatrazescapee.primalwinter.platform.client.XPlatformClient;
import com.alcatrazescapee.primalwinter.util.Config;
import com.alcatrazescapee.primalwinter.util.WeatherHelper;

public final class ClientEventHandler
{
    private static float prevFogDensity = -1f;
    private static long prevFogTick = -1L;
    private static ResourceKey<Level> prevFogDimension;

    public static void setupClient()
    {
        XPlatformClient.INSTANCE.setRenderType(PrimalWinterBlocks.SNOWY_MANGROVE_ROOTS.get(), RenderType.cutout());
        XPlatformClient.INSTANCE.setRenderType(PrimalWinterBlocks.SNOWY_VINE.get(), RenderType.cutout());
    }

    public static void setupBlockColors(BlockColorCallback colors)
    {
        colors.accept((state, world, pos, tintIndex) -> tintIndex == 0 ? FoliageColor.getEvergreenColor() : 0, PrimalWinterBlocks.SNOWY_SPRUCE_LEAVES.get());
        colors.accept((state, world, pos, tintIndex) -> tintIndex == 0 ? FoliageColor.getBirchColor() : 0, PrimalWinterBlocks.SNOWY_BIRCH_LEAVES.get());
        colors.accept((state, world, pos, tintIndex) -> {
            if (tintIndex == 0)
            {
                return world != null && pos != null ? BiomeColors.getAverageFoliageColor(world, pos) : FoliageColor.getDefaultColor();
            }
            return 0xFFFFFF;
        }, PrimalWinterBlocks.SNOWY_OAK_LEAVES.get(), PrimalWinterBlocks.SNOWY_DARK_OAK_LEAVES.get(), PrimalWinterBlocks.SNOWY_JUNGLE_LEAVES.get(), PrimalWinterBlocks.SNOWY_ACACIA_LEAVES.get(), PrimalWinterBlocks.SNOWY_MANGROVE_LEAVES.get(), PrimalWinterBlocks.SNOWY_VINE.get());
    }

    public static void setupItemColors(ItemColorCallback colors)
    {
        colors.accept((stack, tintIndex) -> {
            if (tintIndex == 0)
            {
                BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
                return Minecraft.getInstance().getBlockColors().getColor(state, null, null, tintIndex);
            }
            return 0xFFFFFF;
        }, PrimalWinterBlocks.SNOWY_VINE.get(), PrimalWinterBlocks.SNOWY_OAK_LEAVES.get(), PrimalWinterBlocks.SNOWY_SPRUCE_LEAVES.get(), PrimalWinterBlocks.SNOWY_BIRCH_LEAVES.get(), PrimalWinterBlocks.SNOWY_JUNGLE_LEAVES.get(), PrimalWinterBlocks.SNOWY_ACACIA_LEAVES.get(), PrimalWinterBlocks.SNOWY_DARK_OAK_LEAVES.get());
    }

    public static void setupParticleFactories(ParticleProviderCallback particles)
    {
        particles.accept(PrimalWinterAmbience.SNOW.get(), SnowParticle.Provider::new);
    }

    public static void renderFogColors(Camera camera, float partialTick, FogColorCallback callback)
    {
        if (camera.getEntity() instanceof Player player
                && camera.getFluidInCamera() == FogType.NONE
                && prevFogDensity > 0f
                && WeatherHelper.isSnowingAt(player.level(), camera.getBlockPosition()))
        {
            // Calculate color based on time of day
            final float angle = player.level().getSunAngle(partialTick);
            final float height = Mth.cos(angle);
            final float delta = Mth.clamp((height + 0.4f) / 0.8f, 0, 1);

            final int colorDay = Config.INSTANCE.fogColorDay.getAsInt();
            final int colorNight = Config.INSTANCE.fogColorNight.getAsInt();
            final float red = ((colorDay >> 16) & 0xFF) * delta + ((colorNight >> 16) & 0xFF) * (1 - delta);
            final float green = ((colorDay >> 8) & 0xFF) * delta + ((colorNight >> 8) & 0xFF) * (1 - delta);
            final float blue = (colorDay & 0xFF) * delta + (colorNight & 0xFF) * (1 - delta);

            callback.accept(red / 255f, green / 255f, blue / 255f);
        }
    }

    public static void renderFogDensity(Camera camera, FogDensityCallback callback)
    {
        if (!(camera.getEntity() instanceof Player player))
        {
            prevFogDensity = -1f;
            prevFogTick = -1L;
            prevFogDimension = null;
            return;
        }

        final Level level = player.level();
        if (!level.dimension().equals(prevFogDimension))
        {
            // Fog state is client-global; reset it when changing dimensions so the old world's
            // interpolation cannot bleed into the new level.
            prevFogDensity = -1f;
            prevFogTick = -1L;
            prevFogDimension = level.dimension();
        }

        final BlockPos cameraPos = camera.getBlockPosition();
            final long thisTick = Util.getMillis();
            final boolean firstTick = prevFogTick == -1;
            final float deltaTick = firstTick ? 1e10f : (thisTick - prevFogTick) * 0.00015f;

            prevFogTick = thisTick;

            float expectedFogDensity = 0f;

            if (WeatherHelper.isSnowingAt(level, cameraPos))
            {
                final int light = level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition()));
                expectedFogDensity = Mth.clampedMap(light, 0f, 15f, 0f, 1f);
            }

            // Smoothly interpolate fog towards the expected value - increasing faster than it decreases
            if (expectedFogDensity > prevFogDensity)
            {
                prevFogDensity = Math.min(prevFogDensity + 4f * deltaTick, expectedFogDensity);
            }
            else if (expectedFogDensity < prevFogDensity)
            {
                prevFogDensity = Math.max(prevFogDensity - deltaTick, expectedFogDensity);
            }

            if (camera.getFluidInCamera() != FogType.NONE)
            {
                prevFogDensity = -1; // Immediately cancel fog if there's another fog effect going on
                prevFogTick = -1;
            }

            if (prevFogDensity > 0)
            {
                final float scaledDelta = 1 - (1 - prevFogDensity) * (1 - prevFogDensity);
                final float fogDensity = Config.INSTANCE.fogDensity.getAsFloat();
                final float farPlaneScale = Mth.lerp(scaledDelta, 1f, fogDensity);
                final float nearPlaneScale = Mth.lerp(scaledDelta, 1f, 0.3f * fogDensity);
                callback.accept(nearPlaneScale, farPlaneScale);
            }
    }
}
