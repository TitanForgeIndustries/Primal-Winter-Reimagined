package io.github.usernamec446.winterfront.platform;

import java.nio.file.Path;

import net.minecraft.core.Registry;
import net.minecraftforge.api.distmarker.Dist;
import io.github.usernamec446.winterfront.WinterFrontFrostEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgePlatform implements XPlatform
{
    @Override
    public <T> RegistryInterface<T> registryInterface(Registry<T> registry)
    {
        return new ForgeRegistryInterface<>(registry);
    }

    @Override
    public boolean isDedicatedClient()
    {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public boolean isFrostCancelled(ServerPlayer player)
    {
        return MinecraftForge.EVENT_BUS.post(new WinterFrontFrostEvent(player));
    }

    @Override
    public boolean isModLoaded(String modId)
    {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir()
    {
        return FMLPaths.CONFIGDIR.get();
    }
}
