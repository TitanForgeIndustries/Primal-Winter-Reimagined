package io.github.usernamec446.winterfront.util;

import java.util.Optional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.storm.StormCommands;
import io.github.usernamec446.winterfront.storm.SnowSettings;
import io.github.usernamec446.winterfront.storm.StormManager;
import io.github.usernamec446.winterfront.storm.StormProfile;
import io.github.usernamec446.winterfront.storm.StormProfiles;


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

        final StormProfile profile = StormProfiles.forDimension(level.dimension());
        final BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getBlockRandomPos(chunk.getPos().getMinBlockX(), 0, chunk.getPos().getMinBlockZ(), 15));
        final Optional<SnowSettings.BiomeRule> rule = profile.biomeRule(level.getBiome(pos));
        final float multiplier = StormManager.accumulationMultiplier(level) * rule.map(SnowSettings.BiomeRule::accumulationMultiplier).orElse(1f);
        if (level.random.nextFloat() >= profile.snow().chancePerChunkTick() * multiplier || !WeatherHelper.allowsSnowSettling(level, pos) || !WeatherHelper.isSnowingAt(level, pos))
        {
            return;
        }

        // Stack snow layers
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() == Blocks.SNOW)
        {
            final int layers = state.getValue(BlockStateProperties.LAYERS);
            if (layers < profile.snow().layerLimitAt(pos.getY(), rule))
            {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LAYERS, 1 + layers));
            }
        }
    }

}
