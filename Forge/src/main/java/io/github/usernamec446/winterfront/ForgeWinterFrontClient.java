package io.github.usernamec446.winterfront;

import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import io.github.usernamec446.winterfront.client.ClientEventHandler;

public final class ForgeWinterFrontClient
{
    public static void setupClient()
    {
        final IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        forgeBus.addListener((ViewportEvent.ComputeFogColor event) -> ClientEventHandler.renderFogColors(event.getCamera(), (float) event.getPartialTick(), (red, green, blue) -> {
            event.setRed(red);
            event.setBlue(blue);
            event.setGreen(green);
        }));
        forgeBus.addListener((ViewportEvent.RenderFog event) -> ClientEventHandler.renderFogDensity(event.getCamera(), (nearPlane, farPlane) -> {
            event.scaleNearPlaneDistance(nearPlane);
            event.scaleFarPlaneDistance(farPlane);
            event.setCanceled(true);
        }));
    }
}
