package io.github.usernamec446.winterfront;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

import io.github.usernamec446.winterfront.mixin.BiomeAccessor;
import io.github.usernamec446.winterfront.platform.XPlatform;
import io.github.usernamec446.winterfront.storm.StormManager;
import io.github.usernamec446.winterfront.util.Config;
import io.github.usernamec446.winterfront.util.EventHandler;
import io.github.usernamec446.winterfront.util.WeatherHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(WinterFront.MOD_ID)
public final class ForgeWinterFront
{
    private static final float WINTER_TEMPERATURE = -0.5f;

    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, WinterFront.MOD_ID);
    public static final RegistryObject<Codec<? extends Instance>> CODEC = BIOME_MODIFIERS.register("instance", () -> RecordCodecBuilder.create(instance -> instance.group(
        PlacedFeature.LIST_CODEC.fieldOf("surface_structures").forGetter(c -> c.surfaceStructures),
        PlacedFeature.LIST_CODEC.fieldOf("top_layer_modification").forGetter(c -> c.topLayerModification)
    ).apply(instance, Instance::new)));

    public ForgeWinterFront()
    {
        if (ModList.get().isLoaded(LegacyPrimalWinterMappings.LEGACY_MOD_ID))
        {
            throw new IllegalStateException("Winter Front replaces Primal Winter and Primal Winter: Reimagined. Remove the old mod from the mods folder; existing worlds are converted automatically.");
        }

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);
        Config.INSTANCE.onLoad = ForgeConfig::bake;

        WinterFront.earlySetup();

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        final IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        modBus.addListener((FMLCommonSetupEvent event) -> WinterFront.lateSetup());
        BIOME_MODIFIERS.register(modBus);

        forgeBus.addListener(LegacyPrimalWinterMappings::remapMissing);
        forgeBus.addListener((ServerAboutToStartEvent event) -> syncClimateToNetworkField(event.getServer()));
        forgeBus.addListener((RegisterCommandsEvent event) -> EventHandler.registerCommands(event.getDispatcher()));
        forgeBus.addListener((TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level)
            {
                StormManager.tick(level);
            }
        });
        forgeBus.addListener((SleepFinishedTimeEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level)
            {
                StormManager.onSleepFinished(level);
            }
        });
        forgeBus.addListener((MobSpawnEvent.PositionCheck event) -> {
            final EntityType<?> type = event.getEntity().getType();
            if (!WeatherHelper.isWinterActive(event.getLevel().getLevel())
                    && (type == EntityType.POLAR_BEAR || type == EntityType.STRAY))
            {
                // The biome modifier adds these winter spawns to the configured biome data at
                // load time.  PositionCheck is the runtime, server-side gate that keeps that
                // metadata from spawning winter mobs before the shared start-day boundary,
                // including natural and spawner-based attempts.
                event.setResult(Event.Result.DENY);
            }
        });

        if (XPlatform.INSTANCE.isDedicatedClient())
        {
            ForgeWinterFrontClient.setupClient();
        }
    }

    /**
     * Biome.NETWORK_CODEC reads the raw climateSettings field, while Forge keeps biome modifier
     * results in ModifiableBiomeInfo. Without this copy, clients receive the unmodified climate.
     * Modifiers run before ServerAboutToStartEvent, and the login packet encodes biomes later.
     */
    private static void syncClimateToNetworkField(MinecraftServer server)
    {
        server.registryAccess().registryOrThrow(Registries.BIOME).entrySet().forEach(entry -> {
            if (Config.INSTANCE.isWinterBiome(entry.getKey().location()))
            {
                final Biome biome = entry.getValue();
                ((BiomeAccessor) (Object) biome).winterfront$setClimateSettings(biome.getModifiedClimateSettings());
            }
        });
    }

    record Instance(HolderSet<PlacedFeature> surfaceStructures, HolderSet<PlacedFeature> topLayerModification) implements BiomeModifier
    {
        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder)
        {
            if (biome.unwrapKey().map(k -> !Config.INSTANCE.isWinterBiome(k.location())).orElse(true) || phase != Phase.MODIFY)
            {
                return;
            }

            // Prototype: a static cold climate. Clients without the mod decide between rain and
            // snow from this data, so it has to be part of the biome itself.
            builder.getClimateSettings().setHasPrecipitation(true);
            builder.getClimateSettings().setTemperature(WINTER_TEMPERATURE);

            final BiomeGenerationSettingsBuilder settings = builder.getGenerationSettings();
            for (Holder<PlacedFeature> feature : surfaceStructures)
            {
                settings.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, feature);
            }

            for (Holder<PlacedFeature> feature : topLayerModification)
            {
                settings.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
            }

            final MobSpawnSettingsBuilder spawns = builder.getMobSpawnSettings();
            spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.POLAR_BEAR, 60, 1, 3));
            spawns.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.STRAY, 60, 1, 3));
        }

        @Override
        public Codec<? extends BiomeModifier> codec()
        {
            return CODEC.get();
        }
    }
}
