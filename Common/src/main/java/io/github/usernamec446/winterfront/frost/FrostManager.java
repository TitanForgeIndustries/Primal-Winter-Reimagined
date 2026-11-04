package io.github.usernamec446.winterfront.frost;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import io.github.usernamec446.winterfront.compat.SeasonCompat;
import io.github.usernamec446.winterfront.platform.XPlatform;
import io.github.usernamec446.winterfront.storm.StormManager;
import io.github.usernamec446.winterfront.storm.StormType;
import io.github.usernamec446.winterfront.util.WeatherHelper;

/**
 * Freezes players with vanilla's frozen ticks, which clients without the mod already display.
 * Vanilla then applies its own frost overlay, slowdown and, at full freeze, damage.
 */
public final class FrostManager
{
    /** Why a player is or is not freezing. Also shown by /winterfront frost status. */
    public enum Status
    {
        FREEZING, DISABLED, EXTERNAL_TEMPERATURE_MOD, IMMUNE, NO_FROST_STORM, NOT_EXPOSED, ARMOR, ENCHANTMENT, EFFECT, HEAT_SOURCE, CANCELLED_BY_MOD
    }

    private static final int CHECK_INTERVAL_TICKS = 20;
    /** LivingEntity removes this many frozen ticks every tick, outside of powder snow. */
    private static final int VANILLA_DECAY_PER_TICK = 2;

    private static final Map<ServerPlayer, Boolean> FREEZING = new WeakHashMap<>();

    public static void tick(ServerPlayer player)
    {
        final FrostConfig config = FrostConfig.get();
        if (!config.enabled())
        {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS == 0)
        {
            FREEZING.put(player, status(player, config) == Status.FREEZING);
        }
        if (Boolean.TRUE.equals(FREEZING.get(player)))
        {
            final int required = player.getTicksRequiredToFreeze();
            // Staying fully frozen through the decay of the next tick is what lets vanilla deal damage.
            final int limit = config.mode() == FrostConfig.Mode.DAMAGE ? required + VANILLA_DECAY_PER_TICK : Math.round(required * config.maxFreeze());
            final float gainPerTick = required / (config.secondsToFreeze() * 20f);
            final int gain = VANILLA_DECAY_PER_TICK + (int) gainPerTick + (player.getRandom().nextFloat() < gainPerTick % 1f ? 1 : 0);
            player.setTicksFrozen(Math.min(limit, player.getTicksFrozen() + gain));
        }
    }

    public static Status status(ServerPlayer player)
    {
        return status(player, FrostConfig.get());
    }

    private static Status status(ServerPlayer player, FrostConfig config)
    {
        if (!config.enabled())
        {
            return Status.DISABLED;
        }
        if (config.externalTemperatureMods().stream().anyMatch(XPlatform.INSTANCE::isModLoaded))
        {
            return Status.EXTERNAL_TEMPERATURE_MOD;
        }
        if (player.isCreative() || !player.canFreeze())
        {
            // Creative players, spectators and vanilla's freeze-immune wearables.
            return Status.IMMUNE;
        }
        final ServerLevel level = player.serverLevel();
        if (!StormManager.activeStorm(level).map(StormType::frost).orElse(false))
        {
            return Status.NO_FROST_STORM;
        }
        final BlockPos pos = player.blockPosition();
        final boolean exposed = config.skyExposureRequired()
            ? WeatherHelper.isSnowingAt(level, pos)
            : WeatherHelper.isActiveWinterBiome(level, pos) && level.isRaining() && SeasonCompat.isColdEnoughToSnow(level, pos);
        if (!exposed)
        {
            return Status.NOT_EXPOSED;
        }
        final Status protection = protection(player, level, pos, config.protection());
        if (protection != null)
        {
            return protection;
        }
        return XPlatform.INSTANCE.isFrostCancelled(player) ? Status.CANCELLED_BY_MOD : Status.FREEZING;
    }

    private static Status protection(ServerPlayer player, ServerLevel level, BlockPos pos, FrostConfig.Protection protection)
    {
        int pieces = 0;
        for (ItemStack stack : player.getArmorSlots())
        {
            if (!stack.isEmpty() && protection.armor().matches(stack.getItemHolder()))
            {
                pieces++;
            }
        }
        if (pieces >= protection.piecesRequired())
        {
            return Status.ARMOR;
        }
        for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT)
        {
            if (protection.enchantments().matches(BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment)) && EnchantmentHelper.getEnchantmentLevel(enchantment, player) > 0)
            {
                return Status.ENCHANTMENT;
            }
        }
        for (var effect : player.getActiveEffects())
        {
            final MobEffect type = effect.getEffect();
            if (protection.effects().matches(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(type)))
            {
                return Status.EFFECT;
            }
        }
        final int radius = protection.heatRadius();
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius)))
        {
            final BlockState state = level.getBlockState(near);
            if (!state.isAir() && protection.heatBlocks().matches(state.getBlockHolder())
                && (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)))
            {
                return Status.HEAT_SOURCE;
            }
        }
        return null;
    }

    private FrostManager() {}
}
