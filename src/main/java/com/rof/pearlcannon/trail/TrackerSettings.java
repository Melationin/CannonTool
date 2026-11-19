package com.rof.pearlcannon.trail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家的 tracker 设置：HUD、连线、方块标记与模式。
 * <p>存于 {@code <world>/pearlcannon/trackers/<uuid>.json}；字段初始化即缺省值。
 * 模式只持久化 OFF/ALWAYS，ONCE 是一次性运行态，保存时按 OFF 处理。</p>
 */
public final class TrackerSettings
{
    private static final Logger LOGGER = LoggerFactory.getLogger("pearlcannon");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String ROOT_FOLDER = "pearlcannon";
    private static final String FOLDER = "trackers";
    private static final String EXTENSION = ".json";

    private static final Map<UUID, TrackerSettings> CACHE = new HashMap<>();

    private static Path directory;

    public boolean hud = true;
    public boolean trail = true;
    public boolean box = false;
    public String mode = "OFF";

    public TrackerSettings() {}

    public static void onServerStarted(MinecraftServer server)
    {
        directory = server.getWorldPath(LevelResource.ROOT).resolve(ROOT_FOLDER).resolve(FOLDER);
        CACHE.clear();
        try
        {
            Files.createDirectories(directory);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to create tracker settings directory {}", directory, e);
        }
    }

    public static void onServerStopping()
    {
        CACHE.clear();
        directory = null;
    }

    public static TrackerSettings load(UUID ownerId)
    {
        TrackerSettings cached = CACHE.get(ownerId);
        if (cached != null) return cached;

        TrackerSettings settings = new TrackerSettings();
        if (directory != null)
        {
            Path file = file(ownerId);
            if (Files.isRegularFile(file))
            {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
                {
                    TrackerSettings loaded = GSON.fromJson(reader, TrackerSettings.class);
                    if (loaded != null) settings = loaded;
                }
                catch (Exception e)
                {
                    LOGGER.error("Failed to load tracker settings {}", file, e);
                }
            }
        }

        settings.normalize();
        CACHE.put(ownerId, settings);
        return settings;
    }

    public static void save(UUID ownerId, String mode, boolean hud, boolean trail, boolean box)
    {
        TrackerSettings settings = new TrackerSettings();
        settings.mode = mode;
        settings.hud = hud;
        settings.trail = trail;
        settings.box = box;
        settings.normalize();
        CACHE.put(ownerId, settings);

        if (directory == null) return;

        try
        {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(file(ownerId), StandardCharsets.UTF_8))
            {
                GSON.toJson(settings, writer);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to save tracker settings {}", file(ownerId), e);
        }
    }

    public boolean isAlways()
    {
        return "ALWAYS".equals(mode);
    }

    private void normalize()
    {
        if (!"ALWAYS".equals(mode)) mode = "OFF";
    }

    private static Path file(UUID ownerId)
    {
        return directory.resolve(ownerId + EXTENSION);
    }
}
