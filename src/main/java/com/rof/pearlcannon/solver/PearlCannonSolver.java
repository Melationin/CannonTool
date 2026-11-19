package com.rof.pearlcannon.solver;

import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonRom;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 珍珠炮求解：对每个候选 tick 求闭式的蓝/红药量实数解，再取最近的整数组合模拟校验。
 * <p>速度模型：v = 基础动量 + 蓝数 × 蓝单位向量 + 红数 × 红单位向量，之后按珍珠自身 tick 的
 * {@code vy -= 0.03; v *= 0.99; pos += v} 推进。{@code origin} 是计算时执行命令的玩家坐标，
 * 不存进配置。</p>
 */
public final class PearlCannonSolver
{
    public static final double DRAG = 0.99;
    public static final double GRAVITY = 0.03;
    private static final double MIN_DET = 1.0E-6;
    private static final int MAX_RESULTS = 10;

    private PearlCannonSolver() {}

    public record Solution(String group, int blue, int red, int ticks, double error, Vec3 end) {}

    /** 某个方向组的可解算性信息，用于无解时给出诊断。 */
    public record GroupInfo(String group, boolean complete, double determinant,
                            double blueLength, double redLength, double cosine) {}

    public static List<GroupInfo> inspectGroups(PearlCannonConfig config)
    {
        List<GroupInfo> infos = new ArrayList<>();
        Vec3 emptyDelta = config != null && config.hasEmpty()
                ? config.sample(PearlCannonConfig.EMPTY_KEY).delta.toVec3()
                : Vec3.ZERO;

        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            PearlCannonConfig.Sample blueSample = config == null ? null : config.sample(direction + "B");
            PearlCannonConfig.Sample redSample = config == null ? null : config.sample(direction + "R");
            if (blueSample == null || redSample == null)
            {
                infos.add(new GroupInfo(direction, false, 0.0, 0.0, 0.0, 1.0));
                continue;
            }

            Vec3 blue = blueSample.delta.toVec3().subtract(emptyDelta);
            Vec3 red = redSample.delta.toVec3().subtract(emptyDelta);
            double determinant = blue.x * red.z - red.x * blue.z;
            double blueLength = Math.hypot(blue.x, blue.z);
            double redLength = Math.hypot(red.x, red.z);
            double cosine = blueLength > 0 && redLength > 0
                    ? (blue.x * red.x + blue.z * red.z) / (blueLength * redLength)
                    : 1.0;
            infos.add(new GroupInfo(direction, true, determinant, blueLength, redLength, cosine));
        }
        return infos;
    }

    public static List<Solution> solve(PearlCannonConfig config, Vec3 origin, double targetX, double targetZ,
                                       int maxTnt, int maxTicks)
    {
        List<Solution> results = new ArrayList<>();
        if (config == null || !config.hasReference()) return results;

        Vec3 start = origin.add(config.reference.pos.toVec3());
        Vec3 base;
        Vec3 emptyDelta;
        if (config.hasEmpty())
        {
            PearlCannonConfig.Sample empty = config.sample(PearlCannonConfig.EMPTY_KEY);
            base = empty.motionAfter.toVec3();
            emptyDelta = empty.delta.toVec3();
        }
        else
        {
            base = config.reference.motionBefore.toVec3();
            emptyDelta = Vec3.ZERO;
        }

        boolean[] redReachable = null;
        boolean[] blueReachable = null;
        if (PearlCannonRom.configured(config.rom))
        {
            redReachable = PearlCannonRom.reachable(config.rom.red);
            blueReachable = PearlCannonRom.reachable(config.rom.blue);
        }

        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            PearlCannonConfig.Sample blueSample = config.sample(direction + "B");
            PearlCannonConfig.Sample redSample = config.sample(direction + "R");
            if (blueSample == null || redSample == null) continue;

            Vec3 blue = blueSample.delta.toVec3().subtract(emptyDelta);
            Vec3 red = redSample.delta.toVec3().subtract(emptyDelta);

            double det = blue.x * red.z - red.x * blue.z;
            if (Math.abs(det) < MIN_DET) continue;

            for (int ticks = 1; ticks <= maxTicks; ticks++)
            {
                double scale = DRAG * (1.0 - Math.pow(DRAG, ticks)) / (1.0 - DRAG);
                if (!(scale > 0)) continue;

                double needX = (targetX - start.x) / scale - base.x;
                double needZ = (targetZ - start.z) / scale - base.z;
                if (!Double.isFinite(needX) || !Double.isFinite(needZ)) continue;

                double blueReal = (needX * red.z - needZ * red.x) / det;
                double redReal = (blue.x * needZ - blue.z * needX) / det;
                if (!Double.isFinite(blueReal) || !Double.isFinite(redReal)) continue;

                int blueFloor = (int) Math.floor(blueReal);
                int redFloor = (int) Math.floor(redReal);

                Solution best = null;
                Solution second = null;
                for (int db = 0; db <= 1; db++)
                {
                    for (int dr = 0; dr <= 1; dr++)
                    {
                        int blueCount = blueFloor + db;
                        int redCount = redFloor + dr;
                        // 配置了 ROM 时，药量必须能由权重列表凑出来，取最近的可达值
                        if (blueReachable != null) blueCount = PearlCannonRom.nearestReachable(blueReachable, blueCount);
                        if (redReachable != null) redCount = PearlCannonRom.nearestReachable(redReachable, redCount);
                        if (!validCount(blueCount, maxTnt) || !validCount(redCount, maxTnt)) continue;

                        Vec3 end = simulate(start, base.add(blue.scale(blueCount)).add(red.scale(redCount)), ticks);
                        double error = Math.hypot(end.x - targetX, end.z - targetZ);
                        Solution solution = new Solution(direction, blueCount, redCount, ticks, error, end);

                        if (best == null || error < best.error())
                        {
                            second = best;
                            best = solution;
                        }
                        else if (second == null || error < second.error())
                        {
                            second = solution;
                        }
                    }
                }

                if (best != null) results.add(best);
                if (second != null) results.add(second);
            }
        }

        results.sort(Comparator.comparingDouble(Solution::error)
                .thenComparingInt(solution -> solution.blue() + solution.red())
                .thenComparingInt(Solution::ticks));

        List<Solution> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Solution solution : results)
        {
            // 每个组只保留每个 tick 的最优解，输出多条不同 tick 的候选
            if (!seen.add(solution.group() + "/" + solution.ticks())) continue;
            unique.add(solution);
            if (unique.size() >= MAX_RESULTS) break;
        }
        return unique;
    }

    /**
     * 平面模式：以“珍珠向下穿过指定高度平面”作为落点，落点取命中那一 tick 开始时的坐标
     * （原版传送使用的是 {@code oldPosition()}）。
     */
    public static List<Solution> solvePlane(PearlCannonConfig config, Vec3 origin, double targetX, double targetZ,
                                            double planeY, int maxTnt, int maxTicks)
    {
        List<Solution> results = new ArrayList<>();
        if (config == null || !config.hasReference()) return results;

        Vec3 start = origin.add(config.reference.pos.toVec3());
        Vec3 base;
        Vec3 emptyDelta;
        if (config.hasEmpty())
        {
            PearlCannonConfig.Sample empty = config.sample(PearlCannonConfig.EMPTY_KEY);
            base = empty.motionAfter.toVec3();
            emptyDelta = empty.delta.toVec3();
        }
        else
        {
            base = config.reference.motionBefore.toVec3();
            emptyDelta = Vec3.ZERO;
        }

        boolean[] redReachable = null;
        boolean[] blueReachable = null;
        if (PearlCannonRom.configured(config.rom))
        {
            redReachable = PearlCannonRom.reachable(config.rom.red);
            blueReachable = PearlCannonRom.reachable(config.rom.blue);
        }

        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            PearlCannonConfig.Sample blueSample = config.sample(direction + "B");
            PearlCannonConfig.Sample redSample = config.sample(direction + "R");
            if (blueSample == null || redSample == null) continue;

            Vec3 blue = blueSample.delta.toVec3().subtract(emptyDelta);
            Vec3 red = redSample.delta.toVec3().subtract(emptyDelta);

            double det = blue.x * red.z - red.x * blue.z;
            if (Math.abs(det) < MIN_DET) continue;

            Map<Integer, Solution> bestPerTick = new HashMap<>();

            for (int guess = 1; guess <= maxTicks; guess++)
            {
                double scale = DRAG * (1.0 - Math.pow(DRAG, guess)) / (1.0 - DRAG);
                if (!(scale > 0)) continue;

                double needX = (targetX - start.x) / scale - base.x;
                double needZ = (targetZ - start.z) / scale - base.z;
                if (!Double.isFinite(needX) || !Double.isFinite(needZ)) continue;

                double blueReal = (needX * red.z - needZ * red.x) / det;
                double redReal = (blue.x * needZ - blue.z * needX) / det;
                if (!Double.isFinite(blueReal) || !Double.isFinite(redReal)) continue;

                int blueFloor = (int) Math.floor(blueReal);
                int redFloor = (int) Math.floor(redReal);

                for (int db = 0; db <= 1; db++)
                {
                    for (int dr = 0; dr <= 1; dr++)
                    {
                        int blueCount = blueFloor + db;
                        int redCount = redFloor + dr;
                        if (blueReachable != null) blueCount = PearlCannonRom.nearestReachable(blueReachable, blueCount);
                        if (redReachable != null) redCount = PearlCannonRom.nearestReachable(redReachable, redCount);
                        if (!validCount(blueCount, maxTnt) || !validCount(redCount, maxTnt)) continue;

                        Vec3 velocity = base.add(blue.scale(blueCount)).add(red.scale(redCount));
                        Crossing crossing = traceToPlane(start, velocity, planeY, maxTicks, targetX, targetZ);
                        if (crossing == null) continue;

                        Solution solution = new Solution(direction, blueCount, redCount,
                                crossing.tick(), crossing.error(), crossing.point());
                        bestPerTick.merge(crossing.tick(), solution,
                                (existing, candidate) -> candidate.error() < existing.error() ? candidate : existing);
                    }
                }
            }

            results.addAll(bestPerTick.values());
        }

        results.sort(Comparator.comparingDouble(Solution::error)
                .thenComparingInt(solution -> solution.blue() + solution.red())
                .thenComparingInt(Solution::ticks));

        List<Solution> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Solution solution : results)
        {
            if (!seen.add(solution.group() + "/" + solution.ticks())) continue;
            unique.add(solution);
            if (unique.size() >= MAX_RESULTS) break;
        }
        return unique;
    }

    private record Crossing(int tick, Vec3 point, double error) {}

    private static Crossing traceToPlane(Vec3 start, Vec3 velocity, double planeY, int maxTicks,
                                         double targetX, double targetZ)
    {
        Vec3 position = start;
        Vec3 motion = velocity;
        boolean above = position.y > planeY;
        for (int tick = 1; tick <= maxTicks; tick++)
        {
            Vec3 previous = position;
            motion = motion.subtract(0.0, GRAVITY, 0.0).scale(DRAG);
            position = position.add(motion);
            if (above && position.y <= planeY)
            {
                double error = Math.hypot(previous.x - targetX, previous.z - targetZ);
                return new Crossing(tick, previous, error);
            }
            above = position.y > planeY;
        }
        return null;
    }

    /** 按给定药量算出基准 tick 开头的珍珠动量。 */
    public static Vec3 initialVelocity(PearlCannonConfig config, String direction, int blueCount, int redCount)
    {
        Vec3 emptyDelta = config.hasEmpty()
                ? config.sample(PearlCannonConfig.EMPTY_KEY).delta.toVec3()
                : Vec3.ZERO;
        Vec3 velocity = config.hasEmpty()
                ? config.sample(PearlCannonConfig.EMPTY_KEY).motionAfter.toVec3()
                : config.reference.motionBefore.toVec3();

        PearlCannonConfig.Sample blueSample = config.sample(direction + "B");
        if (blueSample != null)
        {
            velocity = velocity.add(blueSample.delta.toVec3().subtract(emptyDelta).scale(blueCount));
        }
        PearlCannonConfig.Sample redSample = config.sample(direction + "R");
        if (redSample != null)
        {
            velocity = velocity.add(redSample.delta.toVec3().subtract(emptyDelta).scale(redCount));
        }
        return velocity;
    }

    private static boolean validCount(int count, int maxTnt)
    {
        if (count < 0) return false;
        return maxTnt <= 0 || count <= maxTnt;
    }

    public static Vec3 simulate(Vec3 position, Vec3 motion, int ticks)
    {
        Vec3 currentPosition = position;
        Vec3 currentMotion = motion;
        for (int i = 0; i < ticks; i++)
        {
            currentMotion = currentMotion.subtract(0.0, GRAVITY, 0.0).scale(DRAG);
            currentPosition = currentPosition.add(currentMotion);
        }
        return currentPosition;
    }
}
