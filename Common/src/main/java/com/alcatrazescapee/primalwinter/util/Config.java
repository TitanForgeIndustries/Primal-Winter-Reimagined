package com.alcatrazescapee.primalwinter.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Loader-agnostic config holder for Primal Winter: Reimagined.
 *
 * <p>This class only holds plain value-holders so that it can live in the Common module and be
 * referenced from both loaders. The actual config file (and its parsing) is owned by each loader.
 * On Forge, {@code ForgeConfig} builds a {@link net.minecraftforge.common.ForgeConfigSpec} which
 * produces {@code config/primalwinter-common.toml} and copies the parsed values into these holders
 * via the {@link #onLoad} hook.</p>
 */
public final class Config
{
    public static final Config INSTANCE = new Config();

    private static final Logger LOGGER = LogUtils.getLogger();

    // Common
    public final BoolValue enableWeatherCommand;

    public final BoolValue enableSnowAccumulationDuringWorldgen;
    public final BoolValue enableSnowAccumulationDuringWeather;

    public final BoolValue invertNonWinterBiomes;
    public final BoolValue invertNonWinterDimensions;

    /** In-game day on which the eternal storm begins. Before this, the skies are kept clear. */
    public final IntValue snowStartDay;

    // Client
    public final FloatValue fogDensity;
    public final IntValue snowDensity;
    public final BoolValue windSounds;
    public final BoolValue snowSounds;

    public final IntValue fogColorDay;
    public final IntValue fogColorNight;

    public final BoolValue weatherRenderChanges;
    public final BoolValue skyRenderChanges;

    private Set<ResourceLocation> nonWinterBiomes;
    private Set<ResourceKey<Level>> nonWinterDimensions;

    /** Installed by the active loader to copy parsed config values into the holders above. */
    public Runnable onLoad = () -> {};

    private Config()
    {
        this.enableWeatherCommand = new BoolValue(false);

        this.enableSnowAccumulationDuringWorldgen = new BoolValue(false);
        this.enableSnowAccumulationDuringWeather = new BoolValue(true);

        this.invertNonWinterBiomes = new BoolValue(false);
        this.invertNonWinterDimensions = new BoolValue(false);

        this.snowStartDay = new IntValue(5);

        this.fogDensity = new FloatValue(0.5f);
        this.snowDensity = new IntValue(15);
        this.windSounds = new BoolValue(true);
        this.snowSounds = new BoolValue(true);

        this.fogColorDay = new IntValue(0xBFBFD8);
        this.fogColorNight = new IntValue(0x0C0C19);

        this.weatherRenderChanges = new BoolValue(true);
        this.skyRenderChanges = new BoolValue(true);

        this.nonWinterBiomes = Set.of(
                Biomes.NETHER_WASTES.location(),
                Biomes.CRIMSON_FOREST.location(),
                Biomes.WARPED_FOREST.location(),
                Biomes.BASALT_DELTAS.location(),
                Biomes.SOUL_SAND_VALLEY.location(),
                Biomes.END_BARRENS.location(),
                Biomes.END_HIGHLANDS.location(),
                Biomes.END_MIDLANDS.location(),
                Biomes.THE_END.location(),
                Biomes.THE_VOID.location()
        );

        this.nonWinterDimensions = Set.of(Level.NETHER, Level.END);
    }

    public void load()
    {
        LOGGER.info("Loading Primal Winter Config");
        onLoad.run();
    }

    public boolean isWinterDimension(ResourceKey<Level> dimension)
    {
        final boolean listed = nonWinterDimensions.contains(dimension);
        return invertNonWinterDimensions.getAsBoolean() ? listed : !listed;
    }

    public boolean isWinterBiome(@Nullable ResourceLocation name)
    {
        if (name == null)
        {
            return false;
        }
        final boolean listed = nonWinterBiomes.contains(name);
        return invertNonWinterBiomes.getAsBoolean() ? listed : !listed;
    }

    /**
     * Replaces the set of biomes that should NOT be frozen, from a list of biome IDs
     * (e.g. {@code "minecraft:plains"}, {@code "terralith:cave/..."}). Invalid IDs are skipped with a warning.
     */
    public void setNonWinterBiomes(Collection<? extends String> ids)
    {
        final Set<ResourceLocation> set = new HashSet<>();
        for (String id : ids)
        {
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null)
            {
                set.add(location);
            }
            else
            {
                LOGGER.warn("Ignoring invalid biome id in nonWinterBiomes config: '{}'", id);
            }
        }
        this.nonWinterBiomes = set;
    }

    /**
     * Replaces the set of dimensions that should NOT be frozen, from a list of dimension IDs
     * (e.g. {@code "minecraft:the_nether"}, {@code "aether:the_aether"}). Invalid IDs are skipped with a warning.
     */
    public void setNonWinterDimensions(Collection<? extends String> ids)
    {
        final Set<ResourceKey<Level>> set = new HashSet<>();
        for (String id : ids)
        {
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null)
            {
                set.add(ResourceKey.create(Registries.DIMENSION, location));
            }
            else
            {
                LOGGER.warn("Ignoring invalid dimension id in nonWinterDimensions config: '{}'", id);
            }
        }
        this.nonWinterDimensions = set;
    }

    public static final class BoolValue implements BooleanSupplier
    {
        private boolean value;

        public BoolValue(boolean defaultValue) { this.value = defaultValue; }

        @Override
        public boolean getAsBoolean() { return value; }

        public void set(boolean value) { this.value = value; }
    }

    public static final class IntValue implements IntSupplier
    {
        private int value;

        public IntValue(int defaultValue) { this.value = defaultValue; }

        @Override
        public int getAsInt() { return value; }

        public void set(int value) { this.value = value; }
    }

    public static final class FloatValue
    {
        private float value;

        public FloatValue(float defaultValue) { this.value = defaultValue; }

        public float getAsFloat() { return value; }

        public void set(float value) { this.value = value; }
    }
}