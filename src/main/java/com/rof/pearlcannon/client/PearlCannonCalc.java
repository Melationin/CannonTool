package com.rof.pearlcannon.client;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rof.pearlcannon.calc.CalcOutput;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.solver.PearlCannonSolver;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端求解与结果展示：读取本地配置，算完只在本地聊天栏输出。 */
public final class PearlCannonCalc
{
    private static final int DEFAULT_MAX_TICKS = 256;

    private static final Map<UUID, CalcOutput.LastResults> LAST_RESULTS = new HashMap<>();

    private static Component hudDisplay;

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
        if (source.getPlayer() == null) return 0;
        Vec3 origin = source.getPlayer().position();
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
                ? PearlCannonSolver.solvePlane(config, origin, targetX, targetZ, planeY, maxTnt, ticks)
                : PearlCannonSolver.solve(config, origin, targetX, targetZ, maxTnt, ticks);

        CalcOutput.LastResults last = new CalcOutput.LastResults(
                name, origin, targetX, targetZ, planeY, plane, ticks, maxTnt, solutions);
        ClientMessages.send(source, CalcOutput.header(last));

        if (solutions.isEmpty())
        {
            for (Component line : CalcOutput.noSolutionDiagnostics(config, last))
            {
                ClientMessages.send(source, line);
            }
            return 0;
        }

        if (source.getPlayer() == null) return 0;
        LAST_RESULTS.put(source.getPlayer().getUUID(), last);

        for (Component row : CalcOutput.rows(last, "/cannonClient expand"))
        {
            source.sendFeedback(row);
        }
        return 1;
    }

    public static int expand(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {
        FabricClientCommandSource source = context.getSource();
        if (source.getPlayer() == null) return 0;

        CalcOutput.LastResults last = LAST_RESULTS.get(source.getPlayer().getUUID());
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

        for (Component line : CalcOutput.expand(config, last, index, "/cannonClient hud"))
        {
            ClientMessages.send(source, line);
        }
        return 1;
    }

    public static int showHud(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {
        FabricClientCommandSource source = context.getSource();
        if (source.getPlayer() == null) return 0;
        CalcOutput.LastResults last = LAST_RESULTS.get(source.getPlayer().getUUID());
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

        Component display = CalcOutput.hud(config, last.solutions().get(index - 1));
        if (display == null)
        {
            ClientMessages.fail(source, "r 无法生成配置编码（检查 ROM 配置）");
            return 0;
        }

        hudDisplay = display;
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
}
