package com.rof.pearlcannon.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 珍珠炮的二进制 ROM：红权重段、蓝权重段、方向码段三部分按配置的顺序排列。
 * <p>红、蓝权重列表由玩家按牌子上从左到右的顺序手动输入，不做镜像。</p>
 */
public final class PearlCannonRom
{
    /** 三段（R=红，B=蓝，D=方向码）的全部排列。 */
    public static final String[] ORDERS = { "RBD", "RDB", "BRD", "BDR", "DRB", "DBR" };

    private PearlCannonRom() {}

    public static String normalizeOrder(String order)
    {
        if (order == null) return null;
        String upper = order.trim().toUpperCase(Locale.ROOT);
        for (String candidate : ORDERS)
        {
            if (candidate.equals(upper)) return candidate;
        }
        return null;
    }

    public static String orderDisplay(String order)
    {
        StringBuilder builder = new StringBuilder();
        for (char section : order.toCharArray())
        {
            if (builder.length() > 0) builder.append('-');
            builder.append(switch (section)
            {
                case 'R' -> "红";
                case 'B' -> "蓝";
                case 'D' -> "方向";
                default -> String.valueOf(section);
            });
        }
        return builder.toString();
    }

    public static boolean configured(PearlCannonConfig.Rom rom)
    {
        return rom != null
                && normalizeOrder(rom.order) != null
                && rom.red != null && !rom.red.isEmpty()
                && rom.blue != null && !rom.blue.isEmpty();
    }

    /** 解析逗号分隔的权重列表。 */
    public static List<Integer> parseWeights(String text)
    {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("权重列表为空");
        List<Integer> weights = new ArrayList<>();
        for (String token : text.split(",", -1))
        {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("权重列表中存在空项");
            int value;
            try
            {
                value = Integer.parseInt(trimmed);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException("无法解析的权重: " + trimmed);
            }
            if (value < 0) throw new IllegalArgumentException("权重不能为负数: " + value);
            weights.add(value);
        }
        if (weights.isEmpty()) throw new IllegalArgumentException("权重列表为空");
        return weights;
    }

    public static List<Integer> reverse(List<Integer> weights)
    {
        List<Integer> result = new ArrayList<>(weights);
        Collections.reverse(result);
        return result;
    }

    public static int total(List<Integer> weights)
    {
        int sum = 0;
        if (weights == null) return 0;
        for (int weight : weights) sum += weight;
        return sum;
    }

    /** 返回 0..总重量 的可达下标数组，reachable[v] 表示能否凑出 v。 */
    public static boolean[] reachable(List<Integer> weights)
    {
        int sum = total(weights);
        boolean[] dp = new boolean[sum + 1];
        dp[0] = true;
        if (weights == null) return dp;
        for (int weight : weights)
        {
            if (weight <= 0) continue;
            for (int value = sum; value >= weight; value--)
            {
                if (dp[value - weight]) dp[value] = true;
            }
        }
        return dp;
    }

    public static int nearestReachable(boolean[] reachable, int value)
    {
        if (reachable.length == 0) return 0;
        int max = reachable.length - 1;
        if (value <= 0) return 0;
        if (value >= max) return max;
        if (reachable[value]) return value;
        for (int delta = 1; delta <= max; delta++)
        {
            int lower = value - delta;
            if (lower >= 0 && reachable[lower]) return lower;
            int upper = value + delta;
            if (upper <= max && reachable[upper]) return upper;
        }
        return max;
    }

    /** 返回与权重列表等长的 0/1 使用表；无法凑出时返回 null。 */
    public static boolean[] decompose(List<Integer> weights, int target)
    {
        if (weights == null || weights.isEmpty() || target < 0) return null;
        int size = weights.size();
        boolean[] dp = new boolean[target + 1];
        int[] previous = new int[target + 1];
        int[] usedIndex = new int[target + 1];
        dp[0] = true;

        for (int index = 0; index < size; index++)
        {
            int weight = weights.get(index);
            if (weight <= 0) continue;
            for (int value = target; value >= weight; value--)
            {
                if (!dp[value] && dp[value - weight])
                {
                    dp[value] = true;
                    previous[value] = value - weight;
                    usedIndex[value] = index;
                }
            }
        }

        if (!dp[target]) return null;

        boolean[] used = new boolean[size];
        int current = target;
        while (current > 0)
        {
            used[usedIndex[current]] = true;
            current = previous[current];
        }
        return used;
    }

    public static String describeBits(List<Integer> weights, boolean[] used)
    {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < weights.size(); index++)
        {
            if (!used[index]) continue;
            if (builder.length() > 0) builder.append('+');
            builder.append(weights.get(index));
        }
        return builder.isEmpty() ? "0" : builder.toString();
    }

    /** 按顺序拼出完整二进制码：R 段、B 段、D 段（方向码两位）。 */
    public static String buildCode(String order, String direction, boolean[] redBits, boolean[] blueBits)
    {
        String normalized = normalizeOrder(order);
        if (normalized == null) return "";
        StringBuilder builder = new StringBuilder();
        for (char section : normalized.toCharArray())
        {
            switch (section)
            {
                case 'R' -> appendBits(builder, redBits);
                case 'B' -> appendBits(builder, blueBits);
                case 'D' -> builder.append(direction);
                default -> { }
            }
        }
        return builder.toString();
    }

    private static void appendBits(StringBuilder builder, boolean[] bits)
    {
        if (bits == null) return;
        for (boolean bit : bits) builder.append(bit ? '1' : '0');
    }

    public static String formatWeights(List<Integer> weights)
    {
        StringBuilder builder = new StringBuilder();
        if (weights == null) return "";
        for (int index = 0; index < weights.size(); index++)
        {
            if (index > 0) builder.append(',');
            builder.append(weights.get(index));
        }
        return builder.toString();
    }
}
