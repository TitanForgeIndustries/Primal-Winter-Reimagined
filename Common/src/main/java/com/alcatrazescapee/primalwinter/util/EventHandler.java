package com.alcatrazescapee.primalwinter.util;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import com.alcatrazescapee.primalwinter.blocks.PrimalWinterBlocks;

public final class EventHandler
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        final boolean enable = Config.INSTANCE.enableWeatherCommand.getAsBoolean();
        LOGGER.info("Vanilla /weather enabled = {}", enable);
        if (!enable)
        {
            // Vanilla weather command... NOT ALLOWED
            dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("weather"));
            dispatcher.register(Commands.literal("weather").executes(source -> {
                source.getSource().sendSuccess(() -> Component.literal("Not even a command can overcome this storm... (This command is disabled by Primal Winter)"), false);
                return 0;
            }));
        }

        dispatcher.register(Commands.literal("primalwinterReloadConfig").requires(c -> c.hasPermission(2)).executes(source -> {
            Config.INSTANCE.load();
            return Command.SINGLE_SUCCESS;
        }));
    }

    /**
     * During {@link ServerLevel#tickChunk(LevelChunk, int)}, places additional snow layers
     */
    public static void placeExtraSnow(ServerLevel level, ChunkAccess chunk)
    {
        if (!WeatherHelper.supportsWinterWeather(level))
        {
            return;
        }

        if (WeatherHelper.canControlWeather(level))
        {
            applyWeatherState(level);
        }
        if (!WeatherHelper.isWinterActive(level))
        {
            return;
        }

        if (Config.INSTANCE.enableSnowAccumulationDuringWeather.getAsBoolean() && level.random.nextInt(16) == 0)
        {
            final int blockX = chunk.getPos().getMinBlockX();
            final int blockZ = chunk.getPos().getMinBlockZ();
            final BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getBlockRandomPos(blockX, 0, blockZ, 15));
            final BlockState state = level.getBlockState(pos);
            if (WeatherHelper.isSnowingAt(level, pos) && level.getBrightness(LightLayer.BLOCK, pos) < 10)
            {
                if (state.getBlock() == Blocks.SNOW)
                {
                    // Stack snow layers
                    final int layers = state.getValue(BlockStateProperties.LAYERS);
                    if (layers < 5)
                    {
                        level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LAYERS, 1 + layers));
                    }

                    final BlockPos belowPos = pos.below();
                    final BlockState belowState = level.getBlockState(belowPos);
                    final Block replacementBlock = PrimalWinterBlocks.SNOWY_TERRAIN_BLOCKS.getOrDefault(belowState.getBlock(), () -> null).get();
                    if (replacementBlock != null)
                    {
                        level.setBlockAndUpdate(belowPos, replacementBlock.defaultBlockState());
                    }
                }
            }
        }
    }

    public static void setLevelToThunder(LevelAccessor maybeLevel)
    {
        if (maybeLevel instanceof ServerLevel level && WeatherHelper.canControlWeather(level))
        {
            // Apply on every level load so a reconnect/restart cannot briefly retain a stale
            // storm before the first chunk tick.  The shared seasonal predicate still keeps
            // pre-start worlds clear and post-start worlds permanently thundering.
            LOGGER.info("Applying Primal Winter weather state for world {}", level.dimension().location());
            applyWeatherState(level);
        }
    }

    /**
     * Keeps the server-authoritative weather state synchronized with the exact winter start day.
     * This is deliberately level-tick driven rather than chunk-tick driven: a dedicated server
     * can have loaded/force-loaded chunks without any player-ticking chunks, and the seasonal
     * transition must still happen at the configured day boundary in that case.
     */
    public static void tickWeatherState(ServerLevel level)
    {
        if (WeatherHelper.canControlWeather(level))
        {
            applyWeatherState(level);
        }
    }

    /** Applies the configured grace period or permanent storm without fighting the game rule. */
    private static void applyWeatherState(ServerLevel level)
    {
        final boolean started = WeatherHelper.isWinterActive(level);
        final GameRules.BooleanValue weatherCycle = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        if (started)
        {
            // Eternal storm: lock the world into permanent thundering snow.
            if (!level.isThundering() || weatherCycle.get())
            {
                level.setWeatherParameters(0, Integer.MAX_VALUE, true, true);
            }
            if (weatherCycle.get())
            {
                weatherCycle.set(false, level.getServer());
            }
        }
        else
        {
            // Grace period: clear weather and leave the normal weather cycle available.
            if (level.isRaining() || level.isThundering())
            {
                level.setWeatherParameters(Integer.MAX_VALUE, 0, false, false);
            }
            if (!weatherCycle.get())
            {
                weatherCycle.set(true, level.getServer());
            }
        }
    }
}
