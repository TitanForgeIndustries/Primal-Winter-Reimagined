package io.github.usernamec446.winterfront.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;
import net.minecraft.core.Registry;


public interface XPlatform
{
    XPlatform INSTANCE = find(XPlatform.class);

    static <T> T find(Class<T> clazz)
    {
        return ServiceLoader.load(clazz)
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }

    <T> RegistryInterface<T> registryInterface(Registry<T> registry);


    // Platform APIs

    boolean isDedicatedClient();

    Path configDir();
}
