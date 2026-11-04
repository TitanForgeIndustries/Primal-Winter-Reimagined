package io.github.usernamec446.winterfront;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge event bus about once a second for a player about to freeze from a storm.
 * Cancel it to protect the player, for example because of custom warm clothing.
 */
@Cancelable
public class WinterFrontFrostEvent extends PlayerEvent
{
    public WinterFrontFrostEvent(Player player)
    {
        super(player);
    }
}
