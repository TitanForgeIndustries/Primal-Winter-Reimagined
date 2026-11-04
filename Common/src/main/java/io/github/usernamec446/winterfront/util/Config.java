package io.github.usernamec446.winterfront.util;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Plain holders for values from config/winterfront/server.toml and client.toml, which are owned by
 * the loader. Weather, snow, mobs and climate live in the JSON files instead.
 */
public final class Config
{
    public static final Config INSTANCE = new Config();

    // Server
    public final BoolValue enableWeatherCommand = new BoolValue(true);
    public final BoolValue worldgenFreezeTopLayer = new BoolValue(true);
    public final BoolValue worldgenDeepSnow = new BoolValue(false);
    public final BoolValue worldgenIceSpikes = new BoolValue(true);
    public final BoolValue worldgenIcePatches = new BoolValue(true);
    public final BoolValue worldgenSnowPatches = new BoolValue(true);
    public final BoolValue worldgenPowderSnowPatches = new BoolValue(true);

    // Client
    public final FloatValue fogDensity = new FloatValue(0.5f);
    public final IntValue snowDensity = new IntValue(15);
    public final BoolValue windSounds = new BoolValue(true);
    public final BoolValue snowSounds = new BoolValue(true);
    public final IntValue fogColorDay = new IntValue(0xBFBFD8);
    public final IntValue fogColorNight = new IntValue(0x0C0C19);
    public final BoolValue weatherRenderChanges = new BoolValue(true);
    public final BoolValue skyRenderChanges = new BoolValue(true);

    private Config() {}

    public static final class BoolValue implements BooleanSupplier
    {
        private volatile boolean value;
        public BoolValue(boolean defaultValue) { this.value = defaultValue; }
        @Override
        public boolean getAsBoolean() { return value; }
        public void set(boolean value) { this.value = value; }
    }

    public static final class IntValue implements IntSupplier
    {
        private volatile int value;
        public IntValue(int defaultValue) { this.value = defaultValue; }
        @Override
        public int getAsInt() { return value; }
        public void set(int value) { this.value = value; }
    }

    public static final class FloatValue
    {
        private volatile float value;
        public FloatValue(float defaultValue) { this.value = defaultValue; }
        public float getAsFloat() { return value; }
        public void set(float value) { this.value = value; }
    }
}
