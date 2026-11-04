package io.github.usernamec446.winterfront.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.storm.StormCommands;
import io.github.usernamec446.winterfront.storm.StormManager;


public final class EventHandler
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float BASE_ACCUMULATION_CHANCE = 1f / 16f;

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        final boolean enable = Config.INSTANCE.enableWeatherCommand.getAsBoolean();
        LOGGER.info("Vanilla /weather enabled = {}", enable);
        if (!enable)
        {
            // Vanilla weather command... NOT ALLOWED
            dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("weather"));
            dispatcher.register(Commands.literal("weather").executes(source -> {
                source.getSource().sendSuccess(() -> Component.literal("Not even a command can overcome this storm... (This command is disabled by Winter Front)"), false);
                return 0;
            }));
        }

        StormCommands.register(dispatcher);
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

        if (!WeatherHelper.isWinterActive(level))
        {
            return;
        }

        if (Config.INSTANCE.enableSnowAccumulationDuringWeather.getAsBoolean() && level.random.nextFloat() < BASE_ACCUMULATION_CHANCE * StormManager.accumulationMultiplier(level))
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

                }
            }
        }
    }

}
