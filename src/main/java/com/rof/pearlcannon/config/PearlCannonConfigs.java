package com.rof.pearlcannon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rof.pearlcannon.util.ConfigNames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务端珍珠炮配置：存档根目录下 pearlcannon/ 里一炮一个 json。 */
public final class PearlCannonConfigs
{
    private static final Logger LOGGER = LoggerFactory.getLogger("pearlcannon");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FOLDER = "pearlcannon";
    private static final String EXTENSION = ".json";

    private static Path directory;
    private static Map<String, PearlCannonConfig> configs = new LinkedHashMap<>();

    private PearlCannonConfigs() {}

    public static void onServerStarted(MinecraftServer server)
    {
        directory = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
        configs = new LinkedHashMap<>();
        loadDirectory();
    }

    public static void onServerStopping(MinecraftServer server)
    {
        configs = new LinkedHashMap<>();
        directory = null;
    }

    private static void loadDirectory()
    {
        if (directory == null) return;
        try
        {
            Files.createDirectories(directory);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to create pearl cannon config directory {}", directory, e);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION))
        {
            for (Path file : stream)
            {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - EXTENSION.length());
                if (!ConfigNames.isValid(name))
                {
                    LOGGER.warn("Skipping pearl cannon config with invalid name: {}", file);
                    continue;
                }

                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
                {
                    PearlCannonConfig config = GSON.fromJson(reader, PearlCannonConfig.class);
                    if (config == null)
                    {
                        LOGGER.warn("Skipping empty pearl cannon config {}", file);
                        continue;
                    }
                    config.normalize();
                    configs.put(name, config);
                }
                catch (Exception e)
                {
                    LOGGER.error("Failed to load pearl cannon config {}", file, e);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to list pearl cannon configs in {}", directory, e);
        }
    }

    public static void save(String name)
    {
        if (directory == null || !ConfigNames.isValid(name)) return;
        PearlCannonConfig config = configs.get(name);
        if (config == null) return;

        try
        {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(file(name), StandardCharsets.UTF_8))
            {
                GSON.toJson(config, writer);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to save pearl cannon config {} to {}", name, directory, e);
        }
    }

    public static List<String> names()
    {
        List<String> names = new ArrayList<>(configs.keySet());
        names.sort(String::compareTo);
        return names;
    }

    public static PearlCannonConfig get(String name)
    {
        return name == null ? null : configs.get(name);
    }

    public static PearlCannonConfig getOrCreate(String name)
    {
        PearlCannonConfig config = configs.get(name);
        if (config == null)
        {
            config = new PearlCannonConfig();
            configs.put(name, config);
            save(name);
        }
        return config;
    }

    public static boolean remove(String name)
    {
        if (configs.remove(name) == null) return false;
        if (directory != null && ConfigNames.isValid(name))
        {
            try
            {
                Files.deleteIfExists(file(name));
            }
            catch (Exception e)
            {
                LOGGER.error("Failed to delete pearl cannon config file {}", file(name), e);
            }
        }
        return true;
    }

    public static String toJson(PearlCannonConfig config)
    {
        return GSON.toJson(config);
    }

    private static Path file(String name)
    {
        return directory.resolve(name + EXTENSION);
    }
}
