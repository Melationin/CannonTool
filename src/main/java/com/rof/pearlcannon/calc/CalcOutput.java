package com.rof.pearlcannon.calc;

import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonRom;
import com.rof.pearlcannon.solver.PearlCannonSolver;
import com.rof.pearlcannon.util.Format;
import com.rof.pearlcannon.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端与服务端共用的计算结果展示：表头、结果行、展开详情、HUD 与无解诊断。
 * <p>命令前缀由调用方传入，其余文本和交互保持一致。</p>
 */
public final class CalcOutput
{
    public record LastResults(String configName, Vec3 origin, double targetX, double targetZ, double planeY,
                              boolean plane, int maxTicks, int maxTnt,
                              List<PearlCannonSolver.Solution> solutions) {}

    public record Code(boolean[] redBits, boolean[] blueBits, String text) {}

    private CalcOutput() {}

    public static Component header(LastResults last)
    {
        if (last.plane())
        {
            return Messages.c("d CannonTool ", "g 配置 ", "w " + last.configName(),
                    "g  目标 ", "w " + Format.f2(last.targetX(), last.targetZ()),
                    "g  平面 y ", "w " + Format.f3(last.planeY()),
                    "g  药量上限 ", "w " + (last.maxTnt() <= 0 ? "不限" : String.valueOf(last.maxTnt())),
                    "g  tick 上限 ", "w " + last.maxTicks());
        }
        return Messages.c("d CannonTool ", "g 配置 ", "w " + last.configName(),
                "g  目标 ", "w " + Format.f2(last.targetX(), last.targetZ()),
                "g  药量上限 ", "w " + (last.maxTnt() <= 0 ? "不限" : String.valueOf(last.maxTnt())),
                "g  tick 上限 ", "w " + last.maxTicks());
    }

    public static List<Component> rows(LastResults last, String expandCommand)
    {
        List<Component> rows = new ArrayList<>(last.solutions().size());
        for (int index = 0; index < last.solutions().size(); index++)
        {
            rows.add(row(index + 1, last.solutions().get(index), expandCommand));
        }
        return rows;
    }

    public static Component row(int index, PearlCannonSolver.Solution solution, String expandCommand)
    {
        return Messages.c(
                "g #" + index,
                "g   tick ", "y " + solution.ticks(),
                "g   落点 ", "w " + Format.vec(solution.end()),
                "g   误差 ", "y " + Format.f3(solution.error()),
                "g   总数 ", "w " + (solution.blue() + solution.red()),
                "d   [展开]", "!" + expandCommand + " " + index, "^ 点击查看详细信息");
    }

    public static List<Component> expand(PearlCannonConfig config, LastResults last, int index, String hudCommand)
    {
        PearlCannonSolver.Solution solution = last.solutions().get(index - 1);
        int total = solution.blue() + solution.red();
        Vec3 velocity = PearlCannonSolver.initialVelocity(config, solution.group(), solution.blue(), solution.red());

        List<Component> lines = new ArrayList<>();
        lines.add(Messages.c("d CannonTool ", "g 配置 ", "w " + last.configName(), "g  结果 ", "y #" + index));
        if (last.plane())
        {
            lines.add(Messages.c("g   模式 ", "w 平面 y=" + Format.f3(last.planeY())));
        }
        lines.add(Messages.c("g   方向 ", "w " + solution.group(),
                "g   tick ", "w " + solution.ticks(),
                "g   总数 ", "w " + total,
                "g （蓝 ", "w " + solution.blue(), "g  / 红 ", "w " + solution.red(), "g ）"));
        lines.add(Messages.c("g   初速度 ", "w " + Format.vec(velocity)));

        Code code = buildCode(config, solution);
        if (code != null)
        {
            MutableComponent colored = coloredCode(config.rom, solution.group(), code.redBits(), code.blueBits());
            lines.add(Messages.c("g   配置编码 ", colored,
                    "g   ", "d [复制]", "&" + code.text(), "^ 点击复制配置编码",
                    "g   ", "d [HUD]", "!" + hudCommand + " " + index, "^ 点击显示在屏幕上"));
            lines.add(Messages.c("g   红阵列 ", "r " + PearlCannonRom.describeBits(config.rom.red, code.redBits())));
            lines.add(Messages.c("g   蓝阵列 ", "c " + PearlCannonRom.describeBits(config.rom.blue, code.blueBits())));
        }

        lines.add(Messages.c("g   落点 ", "w " + Format.vec(solution.end()),
                "g   误差 ", "y " + Format.f3(solution.error())));
        return lines;
    }

    public static List<Component> noSolutionDiagnostics(PearlCannonConfig config, LastResults last)
    {
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.c("r 没有找到可行解"));

        if (last.plane())
        {
            lines.add(Messages.c("g 平面 y ", "y " + Format.f3(last.planeY()),
                    "g  ，tick 上限 ", "y " + last.maxTicks(), "g  ；可调整平面高度或上限"));
        }
        else
        {
            Vec3 start = last.origin().add(config.reference.pos.toVec3());
            double distance = Math.hypot(last.targetX() - start.x, last.targetZ() - start.z);
            double scale = PearlCannonSolver.DRAG * (1.0 - Math.pow(PearlCannonSolver.DRAG, last.maxTicks()))
                    / (1.0 - PearlCannonSolver.DRAG);
            lines.add(Messages.c("g 水平距离 ", "y " + Format.f3(distance),
                    "g  ，位移系数 ", "y " + Format.f3(scale),
                    "g  ，需要水平速度约 ", "y " + Format.f3(scale > 0 ? distance / scale : 0.0), "g  格/tick"));
        }

        boolean anyUsable = false;
        for (PearlCannonSolver.GroupInfo info : PearlCannonSolver.inspectGroups(config))
        {
            if (!info.complete())
            {
                lines.add(Messages.c("g 组 ", "y " + info.group(), "f  ：蓝红样本不全"));
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, info.cosine()))));
            boolean degenerate = Math.abs(info.determinant()) < 1.0E-6;
            if (!degenerate) anyUsable = true;
            lines.add(Messages.c("g 组 ", "y " + info.group(),
                    "g  ：det ", "w " + Format.f5(info.determinant()),
                    "g  ，蓝 ", "w " + Format.f3(info.blueLength()),
                    "g  ，红 ", "w " + Format.f3(info.redLength()),
                    "g  ，水平夹角 ", "w " + Format.f3(angle) + "°",
                    degenerate ? "r   平行/退化" : ""));
        }

        if (!anyUsable)
        {
            lines.add(Messages.c("y 蓝/红向量平行，无法组合方向；请 resetTick 后重选基准 tick"));
        }

        if (allSamplesIdentical(config))
        {
            lines.add(Messages.c("y 样本 Δv 相同，请确认每次阵列都已切换"));
        }
        return lines;
    }

    public static Component hud(PearlCannonConfig config, PearlCannonSolver.Solution solution)
    {
        Code code = buildCode(config, solution);
        if (code == null) return null;
        return Messages.c("g 配置编码 ",
                coloredCode(config.rom, solution.group(), code.redBits(), code.blueBits()));
    }

    public static Code buildCode(PearlCannonConfig config, PearlCannonSolver.Solution solution)
    {
        if (!PearlCannonRom.configured(config.rom)) return null;
        boolean[] redBits = PearlCannonRom.decompose(config.rom.red, solution.red());
        boolean[] blueBits = PearlCannonRom.decompose(config.rom.blue, solution.blue());
        if (redBits == null || blueBits == null) return null;
        String text = PearlCannonRom.buildCode(config.rom.order, solution.group(), redBits, blueBits);
        return new Code(redBits, blueBits, text);
    }

    public static MutableComponent coloredCode(PearlCannonConfig.Rom rom, String direction,
                                               boolean[] redBits, boolean[] blueBits)
    {
        MutableComponent code = Component.literal("");
        String order = PearlCannonRom.normalizeOrder(rom.order);
        if (order == null) return code;

        for (char section : order.toCharArray())
        {
            switch (section)
            {
                case 'R' -> appendBits(code, redBits, ChatFormatting.RED);
                case 'B' -> appendBits(code, blueBits, ChatFormatting.AQUA);
                case 'D' -> code.append(Component.literal(direction).withStyle(ChatFormatting.WHITE));
                default -> { }
            }
        }
        return code;
    }

    private static void appendBits(MutableComponent code, boolean[] bits, ChatFormatting color)
    {
        if (bits == null) return;
        for (boolean bit : bits)
        {
            code.append(Component.literal(bit ? "1" : "0").withStyle(color));
        }
    }

    private static boolean allSamplesIdentical(PearlCannonConfig config)
    {
        Vec3 first = null;
        for (PearlCannonConfig.Sample sample : config.samples.values())
        {
            if (sample == null || sample.delta == null) continue;
            Vec3 delta = sample.delta.toVec3();
            if (first == null)
            {
                first = delta;
                continue;
            }
            if (delta.distanceToSqr(first) > 1.0E-12) return false;
        }
        return first != null;
    }
}
