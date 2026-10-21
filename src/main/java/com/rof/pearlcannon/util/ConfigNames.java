package com.rof.pearlcannon.util;

import java.util.Locale;

/** 配置名同时用作服务端存档文件与客户端配置文件的文件名，需要过滤路径分隔符和系统保留名。 */
public final class ConfigNames
{
    private static final String[] RESERVED =
            { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
              "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

    private ConfigNames() {}

    public static boolean isValid(String name)
    {
        if (name == null || name.isEmpty() || name.length() > 64) return false;
        if (name.equals(".") || name.equals("..")) return false;
        if (name.endsWith(" ") || name.endsWith(".")) return false;

        for (int index = 0; index < name.length(); index++)
        {
            char c = name.charAt(index);
            if (c < ' ' || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|')
            {
                return false;
            }
        }
        return !isReserved(name);
    }

    private static boolean isReserved(String name)
    {
        String base = name;
        int dot = base.indexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
        String upper = base.toUpperCase(Locale.ROOT);
        for (String reserved : RESERVED)
        {
            if (reserved.equals(upper)) return true;
        }
        return false;
    }
}
