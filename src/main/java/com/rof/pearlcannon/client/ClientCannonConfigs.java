package com.rof.pearlcannon.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rof.pearlcannon.PearlCannon;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.util.ConfigNames;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 客户端本地配置：.minecraft/config/pearlcannon/ 里一炮一个 json，按需读取，手动复制文件也能立刻生效。 */
public final class ClientCannonConfigs
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "pearlcannon";
    private static final String EXTENSION = ".json";

    private ClientCannonConfigs() {}

    public static Path directory()
    {
        return FabricLoader.getInstance().getConfigDir().resolve(FOLDER);
    }

    public static List<String> names()
    {
        Path directory = directory();
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) return names;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION))
        {
            for (Path file : stream)
            {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - EXTENSION.length());
                if (ConfigNames.isValid(name)) names.add(name);
            }
        }
        catch (Exception e)
        {
            PearlCannon.LOGGER.error("Failed to list client pearl cannon configs in {}", directory, e);
        }
        names.sort(String::compareTo);
        return names;
    }

    public static PearlCannonConfig get(String name)
    {
        if (!ConfigNames.isValid(name)) return null;
        Path file = file(name);
        if (!Files.isRegularFile(file)) return null;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            PearlCannonConfig config = GSON.fromJson(reader, PearlCannonConfig.class);
            if (config == null) return null;
            config.normalize();
            return config;
        }
        catch (Exception e)
        {
            PearlCannon.LOGGER.error("Failed to read client pearl cannon config {}", file, e);
            return null;
        }
    }

    /** 读取原始 json，用于推送到服务端。 */
    public static String json(String name)
    {
        if (!ConfigNames.isValid(name)) return null;
        Path file = file(name);
        if (!Files.isRegularFile(file)) return null;

        try
        {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            PearlCannon.LOGGER.error("Failed to read client pearl cannon config {}", file, e);
            return null;
        }
    }

    /** 保存服务端下发的 json；名称或 json 无效时返回 false。 */
    public static boolean save(String name, String json)
    {
        if (!ConfigNames.isValid(name)) return false;

        try
        {
            PearlCannonConfig config = GSON.fromJson(json, PearlCannonConfig.class);
            if (config == null) return false;
            config.normalize();

            Path directory = directory();
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(file(name), StandardCharsets.UTF_8))
            {
                GSON.toJson(config, writer);
            }
            return true;
        }
        catch (Exception e)
        {
            PearlCannon.LOGGER.error("Failed to save client pearl cannon config {}", name, e);
            return false;
        }
    }

    private static Path file(String name)
    {
        return directory().resolve(name + EXTENSION);
    }
}
