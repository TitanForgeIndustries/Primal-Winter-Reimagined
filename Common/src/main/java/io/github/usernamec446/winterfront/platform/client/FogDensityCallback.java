package io.github.usernamec446.winterfront.platform.client;

@FunctionalInterface
public interface FogDensityCallback
{
    void accept(float nearPlane, float farPlane);
}
