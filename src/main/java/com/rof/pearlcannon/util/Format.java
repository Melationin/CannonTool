package com.rof.pearlcannon.util;

import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** 聊天输出共用的数字格式。 */
public final class Format
{
    private Format() {}

    public static String vec(Vec3 vec)
    {
        return "(" + f3(vec.x) + ", " + f3(vec.y) + ", " + f3(vec.z) + ")";
    }

    public static String f2(double x, double z)
    {
        return String.format(Locale.ROOT, "(%.2f, %.2f)", x, z);
    }

    public static String f3(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static String f5(double value)
    {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    /** 按显示宽度补空格（中文按 2 格），用于聊天栏的说明列对齐。 */
    public static String padEnd(String text, int width)
    {
        int current = displayWidth(text);
        if (current >= width) return text + " ";
        return text + " ".repeat(width - current);
    }

    private static int displayWidth(String text)
    {
        int width = 0;
        for (int index = 0; index < text.length(); index++)
        {
            width += text.charAt(index) >= 0x2E80 ? 2 : 1;
        }
        return width;
    }
}
