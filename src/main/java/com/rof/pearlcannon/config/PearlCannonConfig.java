package com.rof.pearlcannon.config;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 珍珠炮配置。
 * <p>原点为投掷者位置；不保存 TNT 坐标，只保存“1 颗推进 TNT”在基准 tick 给珍珠带来的动量变化 Δv。
 * 空配置（ZZ）可选：命中时用它的基准 tick 动量作为基础动量，并把它的 Δv 从各样本的 Δv 中扣除。</p>
 */
public class PearlCannonConfig
{
    public static final String[] DIRECTIONS = { "00", "01", "10", "11" };
    public static final String[] COLORS = { "B", "R" };
    public static final String EMPTY_KEY = "ZZ";

    public String dimension = "";
    /** 最大单侧药量，0 表示不限。 */
    public int maxTnt = 0;
    public Vec origin = new Vec();
    public Reference reference = new Reference();
    public Map<String, Sample> samples = new LinkedHashMap<>();
    public Rom rom = new Rom();

    public static class Vec
    {
        public double x;
        public double y;
        public double z;

        public Vec() {}

        public Vec(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static Vec of(Vec3 vec)
        {
            return new Vec(vec.x, vec.y, vec.z);
        }

        public Vec3 toVec3()
        {
            return new Vec3(x, y, z);
        }

        public Vec add(Vec other)
        {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        public Vec subtract(Vec other)
        {
            return new Vec(x - other.x, y - other.y, z - other.z);
        }

        public double length()
        {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public boolean isZero()
        {
            return x * x + y * y + z * z < 1.0E-12;
        }

        public String format()
        {
            return "(%.4f, %.4f, %.4f)".formatted(x, y, z);
        }
    }

    /** 基准 tick 的珍珠状态：pos 相对原点，motionBefore 为冲量施加前，motionAfter 为该 tick 开头（含冲量）。 */
    public static class Reference
    {
        public int tick = 0;
        public Vec pos = new Vec();
        public Vec motionBefore = new Vec();
        public Vec motionAfter = new Vec();

        private void normalize()
        {
            if (pos == null) pos = new Vec();
            if (motionBefore == null) motionBefore = new Vec();
            if (motionAfter == null) motionAfter = new Vec();
        }
    }

    public static class Sample
    {
        public Vec delta = new Vec();
        public Vec pos = new Vec();
        public Vec motionAfter = new Vec();

        private void normalize()
        {
            if (delta == null) delta = new Vec();
            if (pos == null) pos = new Vec();
            if (motionAfter == null) motionAfter = new Vec();
        }
    }

    /**
     * 二进制 ROM：红权重段、蓝权重段、方向码段。
     * <p>红、蓝列表按玩家输入顺序（牌子上从左到右）保存；order 为 PearlCannonRom.ORDERS 之一。</p>
     */
    public static class Rom
    {
        public String order = "";
        public List<Integer> red = new ArrayList<>();
        public List<Integer> blue = new ArrayList<>();
    }

    public boolean hasReference()
    {
        return reference != null && reference.tick > 0;
    }

    /** 补齐 Gson 反序列化后缺失的字段，并清掉列表里的 null。 */
    public void normalize()
    {
        if (dimension == null) dimension = "";
        if (origin == null) origin = new Vec();
        if (reference == null) reference = new Reference();
        reference.normalize();
        if (samples == null) samples = new LinkedHashMap<>();
        samples.entrySet().removeIf(entry -> entry.getValue() == null);
        for (Sample sample : samples.values()) sample.normalize();
        if (rom == null) rom = new Rom();
        if (rom.red == null) rom.red = new ArrayList<>();
        if (rom.blue == null) rom.blue = new ArrayList<>();
        rom.red.removeIf(value -> value == null);
        rom.blue.removeIf(value -> value == null);
        if (rom.order == null) rom.order = "";
    }

    public boolean hasSample(String key)
    {
        return samples != null && samples.containsKey(key);
    }

    public Sample sample(String key)
    {
        return samples == null ? null : samples.get(key);
    }

    public boolean hasEmpty()
    {
        return hasSample(EMPTY_KEY);
    }

    /** 把珍珠名称中的样本名归一化为 00/01/10/11 + B/R，或空配置 ZZ；无法识别返回 null。 */
    public static String sampleKey(String name)
    {
        if (name == null) return null;
        String text = name.trim();
        if (text.isEmpty()) return null;

        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.equals(EMPTY_KEY)) return EMPTY_KEY;
        if (upper.length() == 3 && isDirection(upper.substring(0, 2)))
        {
            char color = upper.charAt(2);
            if (color == 'B' || color == 'R') return upper;
        }
        return null;
    }

    public static boolean isDirection(String direction)
    {
        for (String candidate : DIRECTIONS)
        {
            if (candidate.equals(direction)) return true;
        }
        return false;
    }

    /** 解析 "配置名:样本名" 形式的珍珠名称，返回样本名（已归一化）；不匹配返回 null。 */
    public static String sampleKeyOf(String pearlName, String configName)
    {
        if (pearlName == null || configName == null || configName.isEmpty()) return null;
        int index = pearlName.indexOf(':');
        if (index < 0) index = pearlName.indexOf('：');
        if (index <= 0) return null;
        if (!pearlName.substring(0, index).equals(configName)) return null;
        return sampleKey(pearlName.substring(index + 1));
    }
}
