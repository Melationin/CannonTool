package com.rof.pearlcannon.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.net.PearlCannonPayloads;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * 客户端命令：计算、本地配置列表与拉取。
 * <p>服务端配置命令是独立的 /cannon，客户端不再需要转发。</p>
 */
public final class PearlCannonClientCommands
{
    private static final SuggestionProvider<FabricClientCommandSource> CONFIG_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(ClientCannonConfigs.names(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> SERVER_CONFIG_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(ServerCannonNames.names(), builder);

    private PearlCannonClientCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("cannonClient")
                .executes(context ->
                {
                    sendHelp(context.getSource());
                    return 1;
                });

        root.then(normalCommand());
        root.then(planeCommand());
        root.then(expandCommand());
        root.then(hudCommand());
        root.then(pushCommand());
        root.then(pullCommand());
        root.then(literal("list").executes(context ->
        {
            listConfigs(context.getSource());
            return 1;
        }));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> normalCommand()
    {
        return literal("normal")
                .then(argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .then(argument("x", DoubleArgumentType.doubleArg())
                                .then(argument("z", DoubleArgumentType.doubleArg())
                                        .executes(context -> PearlCannonCalc.calculate(context, false))
                                        .then(argument("maxTicks", IntegerArgumentType.integer(1))
                                                .executes(context -> PearlCannonCalc.calculate(context, false))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> planeCommand()
    {
        return literal("plane")
                .then(argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .then(argument("x", DoubleArgumentType.doubleArg())
                                .then(argument("z", DoubleArgumentType.doubleArg())
                                        .then(argument("y", DoubleArgumentType.doubleArg())
                                                .executes(context -> PearlCannonCalc.calculate(context, true))
                                                .then(argument("maxTicks", IntegerArgumentType.integer(1))
                                                        .executes(context -> PearlCannonCalc.calculate(context, true)))))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> expandCommand()
    {
        return literal("expand")
                .then(argument("index", IntegerArgumentType.integer(1))
                        .executes(PearlCannonCalc::expand));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hudCommand()
    {
        return literal("hud")
                .executes(context ->
                {
                    ClientMessages.fail(context.getSource(), "r 用法：", "g /cannonClient hud <序号|off>");
                    return 0;
                })
                .then(literal("off").executes(PearlCannonCalc::hideHud))
                .then(argument("index", IntegerArgumentType.integer(1))
                        .executes(PearlCannonCalc::showHud));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> pushCommand()
    {
        return literal("push")
                .then(argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .executes(context -> push(context, StringArgumentType.getString(context, "name"))));
    }

    private static int push(CommandContext<FabricClientCommandSource> context, String name)
    {
        FabricClientCommandSource source = context.getSource();
        String json = ClientCannonConfigs.json(name);
        if (json == null)
        {
            ClientMessages.fail(source, "r 本地没有配置 " + name);
            return 0;
        }
        if (!ClientPlayNetworking.canSend(PearlCannonPayloads.PushConfigPayload.TYPE))
        {
            ClientMessages.fail(source, "r 服务端没有安装 CannonTool");
            return 0;
        }

        ClientPlayNetworking.send(new PearlCannonPayloads.PushConfigPayload(name, json));
        ClientMessages.send(source, "d CannonTool ", "g 正在发送配置 ", "w " + name);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> pullCommand()
    {
        return literal("pull")
                .executes(context -> pull(context, ""))
                .then(argument("name", StringArgumentType.word())
                        .suggests(SERVER_CONFIG_SUGGESTIONS)
                        .executes(context -> pull(context, StringArgumentType.getString(context, "name"))));
    }

    private static int pull(CommandContext<FabricClientCommandSource> context, String name)
    {
        FabricClientCommandSource source = context.getSource();
        if (!ClientPlayNetworking.canSend(PearlCannonPayloads.PullRequestPayload.TYPE))
        {
            ClientMessages.fail(source, "r 当前服务器没有安装 CannonTool 服务端，无法拉取配置");
            return 0;
        }

        ClientPlayNetworking.send(new PearlCannonPayloads.PullRequestPayload(name));
        ClientMessages.send(source, "d CannonTool ", "g 正在从服务端拉取 ", "w " + (name.isEmpty() ? "全部配置" : name));
        return 1;
    }

    private static void listConfigs(FabricClientCommandSource source)
    {
        List<String> names = ClientCannonConfigs.names();
        if (names.isEmpty())
        {
            ClientMessages.send(source, "d CannonTool ", "g 本地还没有配置；执行 ", "g /cannonClient pull ", "g <名称>");
            return;
        }

        ClientMessages.send(source, "d CannonTool ", "g 本地配置 ", "w " + names.size(), "g  个");
        for (String name : names)
        {
            PearlCannonConfig config = ClientCannonConfigs.get(name);
            if (config == null)
            {
                ClientMessages.send(source, "g  - ", "w " + name, "r   读取失败");
                continue;
            }
            String tick = config.hasReference() ? String.valueOf(config.reference.tick) : "未选";
            ClientMessages.send(source, "g  - ", "w " + name,
                    "g   基准 tick ", "y " + tick,
                    "g   样本 ", "y " + config.samples.size(),
                    config.hasEmpty() ? "g   含空配置" : "");
        }
    }

    private static void sendHelp(FabricClientCommandSource source)
    {
        ClientMessages.send(source, "d CannonTool ", "g 计算器");
        help(source, "/cannonClient normal <名称> <x> <z> [最大tick]", "tick 模式");
        help(source, "/cannonClient plane <名称> <x> <z> <y> [最大tick]", "平面模式");
        help(source, "/cannonClient expand <序号>", "展开结果");
        help(source, "/cannonClient hud <序号|off>", "持续显示配置编码");
        help(source, "/cannonClient push <名称>", "把本地配置发送到服务端");
        help(source, "/cannonClient pull [名称]", "拉取配置");
        help(source, "/cannonClient list", "查看本地配置");
    }

    private static void help(FabricClientCommandSource source, String command, String description)
    {
        ClientMessages.send(source, "g - " + command, "^ " + description);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name)
    {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type)
    {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
