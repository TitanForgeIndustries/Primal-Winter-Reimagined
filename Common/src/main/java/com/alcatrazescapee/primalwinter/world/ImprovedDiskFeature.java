package com.alcatrazescapee.primalwinter.world;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

import com.alcatrazescapee.primalwinter.util.WeatherHelper;

/**
 * Runtime-gated disk feature used by Primal Winter's snow, powder-snow, and ice patches.
 *
 * <p>The vanilla {@link DiskFeature} writes its configured state directly and has no seasonal
 * hook.  Keeping the guard at the feature entry point means a placed feature attached by the
 * biome modifier is a no-op before the server's winter start day, without touching vanilla or
 * other-mod disk features.</p>
 */
public final class ImprovedDiskFeature extends DiskFeature
{
    public ImprovedDiskFeature(Codec<DiskConfiguration> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DiskConfiguration> context)
    {
        final WorldGenLevel level = context.level();
        return WeatherHelper.isWinterActive(level.getLevel()) && super.place(context);
    }
}
