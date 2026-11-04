package io.github.usernamec446.winterfront;

import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraftforge.common.world.ClimateSettingsBuilder;
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
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

import io.github.usernamec446.winterfront.mixin.BiomeAccessor;
import io.github.usernamec446.winterfront.platform.XPlatform;
import io.github.usernamec446.winterfront.climate.ClimateConfig;
import io.github.usernamec446.winterfront.frost.FrostManager;
import io.github.usernamec446.winterfront.storm.StormManager;
import io.github.usernamec446.winterfront.storm.StormProfile;
import io.github.usernamec446.winterfront.storm.StormProfiles;
import io.github.usernamec446.winterfront.storm.WinterMob;
import io.github.usernamec446.winterfront.world.WinterFrontFeatures;
import io.github.usernamec446.winterfront.util.Config;
import io.github.usernamec446.winterfront.util.EventHandler;
import io.github.usernamec446.winterfront.util.WeatherHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(WinterFront.MOD_ID)
public final class ForgeWinterFront
{
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

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SERVER_SPEC, ForgeConfig.SERVER_FILE);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfig.CLIENT_SPEC, ForgeConfig.CLIENT_FILE);

        WinterFront.earlySetup();

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        final IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        modBus.addListener((FMLCommonSetupEvent event) -> WinterFront.lateSetup());
        modBus.addListener((ModConfigEvent event) -> ForgeConfig.onConfigEvent(event));
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
        forgeBus.addListener((TickEvent.PlayerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player)
            {
                FrostManager.tick(player);
            }
        });
        forgeBus.addListener((SleepFinishedTimeEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level)
            {
                StormManager.onSleepFinished(level);
            }
        });
        forgeBus.addListener(ForgeWinterFront::checkWinterMobSpawn);

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
            final Biome biome = entry.getValue();
            ((BiomeAccessor) (Object) biome).winterfront$setClimateSettings(biome.getModifiedClimateSettings());
        });
    }

    private static boolean isFeatureEnabled(Holder<PlacedFeature> feature)
    {
        final Config config = Config.INSTANCE;
        return feature.is(WinterFrontFeatures.Keys.FREEZE_TOP_LAYER) ? config.worldgenFreezeTopLayer.getAsBoolean()
            : feature.is(WinterFrontFeatures.Keys.ICE_SPIKES) ? config.worldgenIceSpikes.getAsBoolean()
            : feature.is(WinterFrontFeatures.Keys.ICE_PATCH) ? config.worldgenIcePatches.getAsBoolean()
            : feature.is(WinterFrontFeatures.Keys.SNOW_PATCH) ? config.worldgenSnowPatches.getAsBoolean()
            : feature.is(WinterFrontFeatures.Keys.POWDER_SNOW_PATCH) ? config.worldgenPowderSnowPatches.getAsBoolean()
            : true;
    }

    /**
     * Mobs added by the biome modifier apply to the whole biome, whatever the dimension or day, so
     * natural spawns are checked here. Spawners, eggs and the biome's own vanilla mobs are left alone.
     */
    private static void checkWinterMobSpawn(MobSpawnEvent.PositionCheck event)
    {
        final MobSpawnType reason = event.getSpawnType();
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION)
        {
            return;
        }
        final EntityType<?> type = event.getEntity().getType();
        if (StormProfiles.allMobs().stream().noneMatch(mob -> mob.entity() == type))
        {
            return;
        }
        final ServerLevel level = event.getLevel().getLevel();
        final Holder<Biome> biome = level.getBiome(event.getEntity().blockPosition());
        final boolean nativeToBiome = biome.value().modifiableBiomeInfo().getOriginalBiomeInfo().mobSpawnSettings().getMobs(type.getCategory()).unwrap().stream().anyMatch(data -> data.type == type);
        if (nativeToBiome)
        {
            return;
        }
        final StormProfile profile = StormProfiles.forDimension(level.dimension());
        final Optional<WinterMob> mob = profile == null ? Optional.empty() : profile.mob(type);
        if (mob.isEmpty() || !WeatherHelper.isWinterActive(level) || (mob.get().duringStormOnly() && StormManager.activeStorm(level).isEmpty()))
        {
            event.setResult(Event.Result.DENY);
        }
    }

    record Instance(HolderSet<PlacedFeature> surfaceStructures, HolderSet<PlacedFeature> topLayerModification) implements BiomeModifier
    {
        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder)
        {
            final ClimateConfig climate = ClimateConfig.get();
            if (phase != Phase.MODIFY || !climate.isWinterBiome(biome))
            {
                return;
            }

            // Clients without the mod decide between rain and snow from this data, so it has to be
            // part of the biome itself.
            if (climate.rewritesClimate())
            {
                final ClimateSettingsBuilder settings = builder.getClimateSettings();
                settings.setTemperature(climate.temperatureFor(biome, settings.getTemperature()));
                if (climate.forcePrecipitation())
                {
                    settings.setHasPrecipitation(true);
                }
            }

            final BiomeGenerationSettingsBuilder settings = builder.getGenerationSettings();
            for (Holder<PlacedFeature> feature : surfaceStructures)
            {
                if (isFeatureEnabled(feature))
                {
                    settings.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, feature);
                }
            }

            for (Holder<PlacedFeature> feature : topLayerModification)
            {
                if (isFeatureEnabled(feature))
                {
                    settings.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
                }
            }

            final MobSpawnSettingsBuilder spawns = builder.getMobSpawnSettings();
            for (WinterMob mob : StormProfiles.allMobs())
            {
                spawns.addSpawn(mob.category(), new MobSpawnSettings.SpawnerData(mob.entity(), mob.weight(), mob.group().get(0), mob.group().get(1)));
            }
        }

        @Override
        public Codec<? extends BiomeModifier> codec()
        {
            return CODEC.get();
        }
    }
}
