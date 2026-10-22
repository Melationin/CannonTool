package com.rof.pearlcannon.client;

import java.util.ArrayList;
import java.util.List;

/** 服务端配置名缓存，用于 /cannonClient pull 的补全。 */
public final class ServerCannonNames
{
    private static List<String> names = List.of();

    private ServerCannonNames() {}

    public static List<String> names()
    {
        return names;
    }

    public static void set(String text)
    {
        List<String> parsed = new ArrayList<>();
        if (text != null)
        {
            for (String name : text.split("\n"))
            {
                if (!name.isBlank()) parsed.add(name);
            }
        }
        names = List.copyOf(parsed);
    }

    public static void clear()
    {
        names = List.of();
    }
}
