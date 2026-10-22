package com.rof.pearlcannon.client;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonRom;
import com.rof.pearlcannon.solver.PearlCannonSolver;
import com.rof.pearlcannon.util.Format;
import com.rof.pearlcannon.util.Messages;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端求解与结果展示：读取本地配置，算完只在本地聊天栏输出。 */
public final class PearlCannonCalc
{
    private static final int DEFAULT_MAX_TICKS = 256;

    private static final Map<UUID, LastResults> LAST_RESULTS = new HashMap<>();

    private static Component hudDisplay;

    private record LastResults(String configName, double targetX, double targetZ, double planeY, boolean plane,
                               int maxTicks, int maxTnt, List<PearlCannonSolver.Solution> solutions) {}

    private record Code(boolean[] redBits, boolean[] blueBits, String text) {}

    private PearlCannonCalc() {}

    public static void resetState()
    {
        LAST_RESULTS.clear();
        hudDisplay = null;
    }

    public static Component hudDisplay()
    {
        return hudDisplay;
    }

    public static int calculate(CommandContext<FabricClientCommandSource> context, boolean plane) throws CommandSyntaxException
    {
        FabricClientCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        PearlCannonConfig config = ClientCannonConfigs.get(name);
        if (config == null)
        {
            ClientMessages.fail(source, "r 本地没有配置 " + name, "g  ；执行 ", "g /cannonClient pull ", "g " + name);
            return 0;
        }
        if (!config.hasReference())
        {
            ClientMessages.fail(source, "r 配置 ", "w " + name, "r 尚未选择基准 tick");
            return 0;
        }

        double targetX = DoubleArgumentType.getDouble(context, "x");
        double targetZ = DoubleArgumentType.getDouble(context, "z");
        double planeY = plane ? DoubleArgumentType.getDouble(context, "y") : 0.0;
        int ticks = maxTicks(context);
        int maxTnt = config.maxTnt;

        List<PearlCannonSolver.Solution> solutions = plane
                ? PearlCannonSolver.solvePlane(config, targetX, targetZ, planeY, maxTnt, ticks)
                : PearlCannonSolver.solve(config, targetX, targetZ, maxTnt, ticks);

        if (plane)
        {
            ClientMessages.send(source, "d CannonTool ", "g 配置 ", "w " + name,
                    "g  目标 ", "w " + Format.f2(targetX, targetZ),
                    "g  平面 y ", "w " + Format.f3(planeY),
                    "g  药量上限 ", "w " + (maxTnt <= 0 ? "不限" : String.valueOf(maxTnt)),
                    "g  tick 上限 ", "w " + ticks);
        }
        else
        {
            ClientMessages.send(source, "d CannonTool ", "g 配置 ", "w " + name,
                    "g  目标 ", "w " + Format.f2(targetX, targetZ),
                    "g  药量上限 ", "w " + (maxTnt <= 0 ? "不限" : String.valueOf(maxTnt)),
                    "g  tick 上限 ", "w " + ticks);
        }

        if (solutions.isEmpty())
        {
            sendNoSolutionDiagnostics(source, config, targetX, targetZ, planeY, plane, ticks);
            return 0;
        }

        if (source.getPlayer() == null) return 0;
        LAST_RESULTS.put(source.getPlayer().getUUID(),
                new LastResults(name, targetX, targetZ, planeY, plane, ticks, maxTnt, solutions));

        int index = 0;
        for (PearlCannonSolver.Solution solution : solutions)
        {
            index++;
            Component row = Messages.c(
                    "g #" + index,
                    "g   tick ", "y " + solution.ticks(),
                    "g   落点 ", "w " + Format.vec(solution.end()),
                    "g   误差 ", "y " + Format.f3(solution.error()),
                    "g   总数 ", "w " + (solution.blue() + solution.red()),
                    "d   [展开]", "!/cannonClient expand " + index, "^ 点击查看详细信息");
            source.sendFeedback(row);
        }
        return 1;
    }

    public static int expand(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {
        FabricClientCommandSource source = context.getSource();
        if (source.getPlayer() == null) return 0;

        LastResults last = LAST_RESULTS.get(source.getPlayer().getUUID());
        if (last == null)
        {
            ClientMessages.fail(source, "r 还没有计算结果，先执行 ", "g /cannonClient normal ", "g <名称> <x> <z>");
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 1 || index > last.solutions().size())
        {
            ClientMessages.fail(source, "r 序号超出范围（共 " + last.solutions().size() + " 条）");
            return 0;
        }

        PearlCannonConfig config = ClientCannonConfigs.get(last.configName());
        if (config == null)
        {
            ClientMessages.fail(source, "r 本地配置 " + last.configName() + " 已不存在");
            return 0;
        }

        PearlCannonSolver.Solution solution = last.solutions().get(index - 1);
        int total = solution.blue() + solution.red();
        Vec3 velocity = PearlCannonSolver.initialVelocity(config, solution.group(), solution.blue(), solution.red());

        ClientMessages.send(source, "d CannonTool ", "g 配置 ", "w " + last.configName(), "g  结果 ", "y #" + index);
        if (last.plane())
        {
            ClientMessages.send(source, "g   模式 ", "w 平面 y=" + Format.f3(last.planeY()));
        }
        ClientMessages.send(source, "g   方向 ", "w " + solution.group(),
                "g   tick ", "w " + solution.ticks(),
                "g   总数 ", "w " + total,
                "g （蓝 ", "w " + solution.blue(), "g  / 红 ", "w " + solution.red(), "g ）");
        ClientMessages.send(source, "g   初速度 ", "w " + Format.vec(velocity));

        Code code = buildCode(config, solution);
        if (code != null)
        {
            MutableComponent colored = coloredCode(config.rom, solution.group(), code.redBits(), code.blueBits());
            ClientMessages.send(source, "g   配置编码 ", colored,
                    "g   ", "d [复制]", "&" + code.text(), "^ 点击复制配置编码",
                    "g   ", "d [HUD]", "!/cannonClient hud " + index, "^ 点击显示在屏幕上");
            ClientMessages.send(source, "g   红阵列 ", "r " + PearlCannonRom.describeBits(config.rom.red, code.redBits()));
            ClientMessages.send(source, "g   蓝阵列 ", "c " + PearlCannonRom.describeBits(config.rom.blue, code.blueBits()));
        }

        ClientMessages.send(source, "g   落点 ", "w " + Format.vec(solution.end()),
                "g   误差 ", "y " + Format.f3(solution.error()));
        return 1;
    }

    public static int showHud(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {
        FabricClientCommandSource source = context.getSource();
        if (source.getPlayer() == null) return 0;
        LastResults last = LAST_RESULTS.get(source.getPlayer().getUUID());
        if (last == null)
        {
            ClientMessages.fail(source, "r 还没有计算结果");
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 1 || index > last.solutions().size())
        {
            ClientMessages.fail(source, "r 序号超出范围（共 " + last.solutions().size() + " 条）");
            return 0;
        }

        PearlCannonConfig config = ClientCannonConfigs.get(last.configName());
        if (config == null)
        {
            ClientMessages.fail(source, "r 本地配置 " + last.configName() + " 已不存在");
            return 0;
        }

        PearlCannonSolver.Solution solution = last.solutions().get(index - 1);
        Code code = buildCode(config, solution);
        if (code == null)
        {
            ClientMessages.fail(source, "r 无法生成配置编码（检查 ROM 配置）");
            return 0;
        }

        hudDisplay = Messages.c("g 配置编码 ",
                coloredCode(config.rom, solution.group(), code.redBits(), code.blueBits()));
        ClientMessages.send(source, "d CannonTool ", "g 已显示配置编码；关闭：", "g /cannonClient hud off",
                "!/cannonClient hud off", "^ 点击关闭");
        return 1;
    }

    public static int hideHud(CommandContext<FabricClientCommandSource> context)
    {
        FabricClientCommandSource source = context.getSource();
        if (hudDisplay == null)
        {
            ClientMessages.fail(source, "r 当前没有显示配置编码");
            return 0;
        }
        hudDisplay = null;
        ClientMessages.send(source, "d CannonTool ", "g 已隐藏配置编码");
        return 1;
    }

    private static Code buildCode(PearlCannonConfig config, PearlCannonSolver.Solution solution)
    {
        if (!PearlCannonRom.configured(config.rom)) return null;
        boolean[] redBits = PearlCannonRom.decompose(config.rom.red, solution.red());
        boolean[] blueBits = PearlCannonRom.decompose(config.rom.blue, solution.blue());
        if (redBits == null || blueBits == null) return null;
        String text = PearlCannonRom.buildCode(config.rom.order, solution.group(), redBits, blueBits);
        return new Code(redBits, blueBits, text);
    }

    private static MutableComponent coloredCode(PearlCannonConfig.Rom rom, String direction,
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

    private static int maxTicks(CommandContext<FabricClientCommandSource> context)
    {
        try
        {
            return IntegerArgumentType.getInteger(context, "maxTicks");
        }
        catch (IllegalArgumentException e)
        {
            return DEFAULT_MAX_TICKS;
        }
    }

    private static void sendNoSolutionDiagnostics(FabricClientCommandSource source, PearlCannonConfig config,
                                                  double targetX, double targetZ, double planeY, boolean plane, int maxTicks)
    {
        ClientMessages.send(source, "r 没有找到可行解");

        if (plane)
        {
            ClientMessages.send(source, "g 平面 y ", "y " + Format.f3(planeY), "g  ，tick 上限 ", "y " + maxTicks,
                    "g  ；可调整平面高度或上限");
        }
        else
        {
            Vec3 start = config.origin.toVec3().add(config.reference.pos.toVec3());
            double distance = Math.hypot(targetX - start.x, targetZ - start.z);
            double scale = PearlCannonSolver.DRAG * (1.0 - Math.pow(PearlCannonSolver.DRAG, maxTicks))
                    / (1.0 - PearlCannonSolver.DRAG);
            ClientMessages.send(source, "g 水平距离 ", "y " + Format.f3(distance),
                    "g  ，位移系数 ", "y " + Format.f3(scale),
                    "g  ，需要水平速度约 ", "y " + Format.f3(scale > 0 ? distance / scale : 0.0), "g  格/tick");
        }

        boolean anyUsable = false;
        for (PearlCannonSolver.GroupInfo info : PearlCannonSolver.inspectGroups(config))
        {
            if (!info.complete())
            {
                ClientMessages.send(source, "g 组 ", "y " + info.group(), "f  ：蓝红样本不全");
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, info.cosine()))));
            boolean degenerate = Math.abs(info.determinant()) < 1.0E-6;
            if (!degenerate) anyUsable = true;
            ClientMessages.send(source, "g 组 ", "y " + info.group(),
                    "g  ：det ", "w " + Format.f5(info.determinant()),
                    "g  ，蓝 ", "w " + Format.f3(info.blueLength()),
                    "g  ，红 ", "w " + Format.f3(info.redLength()),
                    "g  ，水平夹角 ", "w " + Format.f3(angle) + "°",
                    degenerate ? "r   平行/退化" : "");
        }

        if (!anyUsable)
        {
            ClientMessages.send(source, "y 蓝/红向量平行，无法组合方向；请 resetTick 后重选基准 tick");
        }

        if (allSamplesIdentical(config))
        {
            ClientMessages.send(source, "y 样本 Δv 相同，请确认每次阵列都已切换");
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
