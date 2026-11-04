package io.github.usernamec446.winterfront.storm;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import io.github.usernamec446.winterfront.WinterFront;
import io.github.usernamec446.winterfront.platform.XPlatform;

/**
 * Loads weather profiles. A broken file never crashes the game: at startup the built-in default is
 * used instead, and a reload keeps the previous working profiles.
 */
public final class StormProfiles
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // Read from worldgen threads, so it is only ever replaced as a whole.
    private static volatile Map<ResourceLocation, StormProfile> byDimension = index(List.of(StormProfile.DEFAULT));

    public static StormProfile forDimension(ResourceKey<Level> dimension)
    {
        return byDimension.get(dimension.location());
    }

    /** @return true if every file was valid and the new profiles are now active. */
    public static boolean load()
    {
        final Path directory = XPlatform.INSTANCE.configDir().resolve(WinterFront.MOD_ID).resolve("profiles");
        try
        {
            Files.createDirectories(directory);
            final List<Path> files;
            try (Stream<Path> stream = Files.list(directory))
            {
                files = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            }
            if (files.isEmpty())
            {
                writeDefault(directory.resolve("overworld.json"));
                byDimension = index(List.of(StormProfile.DEFAULT));
                return true;
            }

            final List<StormProfile> profiles = new java.util.ArrayList<>();
            boolean valid = true;
            for (Path file : files)
            {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
                {
                    final StormProfile profile = StormProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .resultOrPartial(error -> LOGGER.error("Invalid weather profile {}: {}", file.getFileName(), error))
                        .orElse(null);
                    valid &= profile != null;
                    if (profile != null)
                    {
                        profiles.add(profile);
                    }
                }
                catch (JsonParseException e)
                {
                    LOGGER.error("Invalid JSON in weather profile {}: {}", file.getFileName(), e.getMessage());
                    valid = false;
                }
            }
            if (!valid)
            {
                LOGGER.error("Weather profiles were not changed because of the errors above");
                return false;
            }
            byDimension = index(profiles);
            LOGGER.info("Loaded {} weather profile(s) for dimensions {}", profiles.size(), byDimension.keySet());
            return true;
        }
        catch (IOException e)
        {
            LOGGER.error("Could not read weather profiles from {}", directory, e);
            return false;
        }
    }

    private static Map<ResourceLocation, StormProfile> index(List<StormProfile> profiles)
    {
        final Map<ResourceLocation, StormProfile> map = new HashMap<>();
        for (StormProfile profile : profiles)
        {
            for (ResourceLocation dimension : profile.dimensions())
            {
                if (map.putIfAbsent(dimension, profile) != null)
                {
                    LOGGER.warn("Dimension {} is listed in more than one weather profile; using the first", dimension);
                }
            }
        }
        return Map.copyOf(map);
    }

    /** Written by hand because codecs omit fields equal to their default, hiding them from admins. */
    private static void writeDefault(Path file) throws IOException
    {
        Files.writeString(file, """
            {
              "dimensions": ["minecraft:overworld"],
              "mode": "cycle",
              "firstStormDay": 5,
              "clearDays": [2.0, 6.0],
              "sleepSkipsStorm": true,
              "stormTypes": [
                { "id": "light_snow", "weight": 60, "days": [0.5, 1.0], "thunder": false, "intensity": 0.4, "accumulationMultiplier": 0.5 },
                { "id": "snowfall", "weight": 30, "days": [0.5, 1.5], "thunder": false, "intensity": 1.0, "accumulationMultiplier": 1.0 },
                { "id": "blizzard", "weight": 10, "days": [0.5, 1.0], "thunder": true, "intensity": 1.0, "accumulationMultiplier": 2.0 }
              ]
            }
            """, StandardCharsets.UTF_8);
    }

    private StormProfiles() {}
}
