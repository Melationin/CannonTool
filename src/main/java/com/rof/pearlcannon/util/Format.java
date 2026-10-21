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
}
