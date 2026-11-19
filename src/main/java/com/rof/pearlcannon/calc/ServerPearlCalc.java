package com.rof.pearlcannon.calc;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import com.rof.pearlcannon.solver.PearlCannonSolver;
import com.rof.pearlcannon.util.Messages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端计算器：使用服务端配置，结果展示与客户端共用 {@link CalcOutput}。
 * <p>计算结果按玩家保存，支持 expand/hud；HUD 通过动作栏每 tick 重发。</p>
 */
public final class ServerPearlCalc
{
    private static final int DEFAULT_MAX_TICKS = 256;

    private static final Map<UUID, CalcOutput.LastResults> LAST_RESULTS = new HashMap<>();
    private static final Map<UUID, Component> HUD = new HashMap<>();

    private static boolean initialized;

    private ServerPearlCalc() {}

    public static void init()
    {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(ServerPearlCalc::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
        {
            ServerPlayer player = handler.getPlayer();
            if (player != null) reset(player.getUUID());
        });
    }

    private static void onServerTick(MinecraftServer server)
    {
        if (HUD.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            Component display = HUD.get(player.getUUID());
            if (display != null) player.sendSystemMessage(display, true);
        }
    }

    private static void reset(UUID playerId)
    {
        LAST_RESULTS.remove(playerId);
        HUD.remove(playerId);
    }

    public static int calculate(CommandContext<CommandSourceStack> context, boolean plane)
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            Messages.fail(source, "r 计算需要玩家执行（源点为玩家坐标）");
            return 0;
        }
        Vec3 origin = player.position();
        String name = StringArgumentType.getString(context, "name");
        PearlCannonConfig config = PearlCannonConfigs.get(name);
        if (config == null)
        {
            Messages.fail(source, "r 服务端没有配置 " + name);
            return 0;
        }
        if (!config.hasReference())
        {
            Messages.fail(source, "r 配置 ", "w " + name, "r 尚未选择基准 tick");
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
        Messages.send(source, CalcOutput.header(last));

        if (solutions.isEmpty())
        {
            for (Component line : CalcOutput.noSolutionDiagnostics(config, last))
            {
                Messages.send(source, line);
            }
            return 0;
        }

        LAST_RESULTS.put(player.getUUID(), last);

        for (Component row : CalcOutput.rows(last, "/cannon calc expand"))
        {
            Messages.send(source, row);
        }
        return 1;
    }

    public static int expand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CalcOutput.LastResults last = LAST_RESULTS.get(player.getUUID());
        if (last == null)
        {
            Messages.fail(source, "r 还没有计算结果，先执行 ", "g /cannon calc normal ", "g <名称> <x> <z>");
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 1 || index > last.solutions().size())
        {
            Messages.fail(source, "r 序号超出范围（共 " + last.solutions().size() + " 条）");
            return 0;
        }

        PearlCannonConfig config = PearlCannonConfigs.get(last.configName());
        if (config == null)
        {
            Messages.fail(source, "r 服务端配置 " + last.configName() + " 已不存在");
            return 0;
        }

        for (Component line : CalcOutput.expand(config, last, index, "/cannon calc hud"))
        {
            Messages.send(source, line);
        }
        return 1;
    }

    public static int showHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CalcOutput.LastResults last = LAST_RESULTS.get(player.getUUID());
        if (last == null)
        {
            Messages.fail(source, "r 还没有计算结果");
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        if (index < 1 || index > last.solutions().size())
        {
            Messages.fail(source, "r 序号超出范围（共 " + last.solutions().size() + " 条）");
            return 0;
        }

        PearlCannonConfig config = PearlCannonConfigs.get(last.configName());
        if (config == null)
        {
            Messages.fail(source, "r 服务端配置 " + last.configName() + " 已不存在");
            return 0;
        }

        Component display = CalcOutput.hud(config, last.solutions().get(index - 1));
        if (display == null)
        {
            Messages.fail(source, "r 无法生成配置编码（检查 ROM 配置）");
            return 0;
        }

        HUD.put(player.getUUID(), display);
        player.sendSystemMessage(display, true);
        Messages.send(source, "d CannonTool ", "g 已显示配置编码；关闭：", "g /cannon calc hud off",
                "!/cannon calc hud off", "^ 点击关闭");
        return 1;
    }

    public static int hideHud(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (HUD.remove(player.getUUID()) == null)
        {
            Messages.fail(source, "r 当前没有显示配置编码");
            return 0;
        }
        player.sendSystemMessage(Component.empty(), true);
        Messages.send(source, "d CannonTool ", "g 已隐藏配置编码");
        return 1;
    }

    private static int maxTicks(CommandContext<CommandSourceStack> context)
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
