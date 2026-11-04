package io.github.usernamec446.winterfront.storm;

import java.util.Optional;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.usernamec446.winterfront.WinterFront;
import io.github.usernamec446.winterfront.climate.ClimateConfig;
import io.github.usernamec446.winterfront.compat.SeasonCompat;
import io.github.usernamec446.winterfront.frost.FrostConfig;
import io.github.usernamec446.winterfront.frost.FrostManager;

public final class StormCommands
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(WinterFront.MOD_ID)
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reload").executes(StormCommands::reload))
            .then(Commands.literal("frost").then(Commands.literal("status")
                .executes(context -> frostStatus(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> frostStatus(context, EntityArgument.getPlayer(context, "player"))))))
            .then(Commands.literal("storm")
                .then(Commands.literal("status").executes(StormCommands::status))
                .then(Commands.literal("stop").executes(StormCommands::stop))
                .then(Commands.literal("start")
                    .executes(context -> start(context, null))
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            final StormProfile profile = StormManager.controllingProfile(context.getSource().getLevel());
                            return SharedSuggestionProvider.suggest(profile == null ? java.util.stream.Stream.<String>empty() : profile.stormTypes().stream().map(StormType::id), builder);
                        })
                        .executes(context -> start(context, StringArgumentType.getString(context, "type")))))));
    }

    private static int reload(CommandContext<CommandSourceStack> context)
    {
        if (StormProfiles.load() & FrostConfig.load())
        {
            context.getSource().sendSuccess(() -> Component.literal("Weather profiles and frost settings reloaded. Changes to climate.json, world generation and the spawn list of mobs need a restart."), true);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(Component.literal("Config has errors, and the broken files were not applied; see the server log"));
        return 0;
    }

    private static int frostStatus(CommandContext<CommandSourceStack> context, ServerPlayer player)
    {
        final FrostManager.Status status = FrostManager.status(player);
        context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + status + ", frozen ticks " + player.getTicksFrozen() + "/" + player.getTicksRequiredToFreeze()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context)
    {
        final ServerLevel level = context.getSource().getLevel();
        final StormProfile profile = requireProfile(context);
        if (profile == null)
        {
            return 0;
        }
        final StormState state = StormState.get(level);
        final String phase = profile.mode() == StormProfile.Mode.VANILLA ? "vanilla weather" : switch (state.phase)
        {
            case NONE -> "starting";
            case WAITING -> "waiting for day " + profile.firstStormDay();
            case CLEAR -> "clear, next storm in " + remaining(level, state);
            case STORM -> "storm '" + state.stormId + "', ends " + (state.endTick == Long.MAX_VALUE ? "never" : "in " + remaining(level, state));
        };
        final String season = ClimateConfig.get().followsSeasons() ? " [season: " + SeasonCompat.currentSeason(level) + "]" : "";
        context.getSource().sendSuccess(() -> Component.literal(level.dimension().location() + ": " + phase + season), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int start(CommandContext<CommandSourceStack> context, String id)
    {
        final ServerLevel level = context.getSource().getLevel();
        final StormProfile profile = requireScheduledProfile(context);
        if (profile == null)
        {
            return 0;
        }
        final Optional<StormType> type = id == null ? Optional.of(profile.randomStormType(level.random)) : profile.stormType(id);
        if (type.isEmpty())
        {
            context.getSource().sendFailure(Component.literal("Unknown storm type '" + id + "'"));
            return 0;
        }
        StormManager.startStorm(level, profile, StormState.get(level), type.get());
        context.getSource().sendSuccess(() -> Component.literal("Started storm '" + type.get().id() + "'"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandContext<CommandSourceStack> context)
    {
        final ServerLevel level = context.getSource().getLevel();
        final StormProfile profile = requireScheduledProfile(context);
        if (profile == null)
        {
            return 0;
        }
        if (profile.mode() == StormProfile.Mode.ETERNAL)
        {
            context.getSource().sendFailure(Component.literal("This dimension uses an eternal storm"));
            return 0;
        }
        StormManager.startClear(level, profile, StormState.get(level));
        context.getSource().sendSuccess(() -> Component.literal("Storm stopped"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static StormProfile requireProfile(CommandContext<CommandSourceStack> context)
    {
        final StormProfile profile = StormManager.controllingProfile(context.getSource().getLevel());
        if (profile == null)
        {
            context.getSource().sendFailure(Component.literal("This dimension has no weather profile, or shares another dimension's weather"));
        }
        return profile;
    }

    private static StormProfile requireScheduledProfile(CommandContext<CommandSourceStack> context)
    {
        final StormProfile profile = requireProfile(context);
        if (profile != null && profile.mode() == StormProfile.Mode.VANILLA)
        {
            context.getSource().sendFailure(Component.literal("This dimension uses vanilla weather; use /weather"));
            return null;
        }
        return profile;
    }

    private static String remaining(ServerLevel level, StormState state)
    {
        final long minutes = Math.max(0L, state.endTick - level.getGameTime()) / (20 * 60);
        return minutes + " min";
    }

    private StormCommands() {}
}
