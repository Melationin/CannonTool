package com.rof.pearlcannon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rof.pearlcannon.calibration.PearlCalibration;
import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import com.rof.pearlcannon.config.PearlCannonRom;
import com.rof.pearlcannon.net.ServerConfigShare;
import com.rof.pearlcannon.solver.PearlCannonSolver;
import com.rof.pearlcannon.trail.ServerPearlTrail;
import com.rof.pearlcannon.util.CommandAccess;
import com.rof.pearlcannon.util.ConfigNames;
import com.rof.pearlcannon.util.Format;
import com.rof.pearlcannon.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 服务端命令：标定、配置、ROM、服务端计算与轨迹。 */
public final class PearlCannonConfigCommands
{
    private static final SuggestionProvider<CommandSourceStack> CONFIG_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PearlCannonConfigs.names(), builder);

    private static final SuggestionProvider<CommandSourceStack> ORDER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PearlCannonRom.ORDERS, builder);

    private static final String[] GIVE_TYPES = { "ender_pearl", "snowball" };

    private static final SuggestionProvider<CommandSourceStack> GIVE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(GIVE_TYPES, builder);

    private static final SuggestionProvider<CommandSourceStack> TRAIL_SUGGESTIONS =
            (context, builder) ->
            {
                List<String> names = new ArrayList<>(PearlCannonConfigs.names());
                names.add("off");
                return SharedSuggestionProvider.suggest(names, builder);
            };

    private record Active(String name, PearlCannonConfig config) {}

    private PearlCannonConfigCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(rootCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rootCommand()
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cannon")
                .requires(CommandAccess::canUse)
                .executes(context ->
                {
                    sendHelp(context.getSource());
                    return 1;
                });

        root.then(calcCommand());
        //? =26.1 {
        if (FabricLoader.getInstance().isModLoaded("ryansserverrenderingkit"))
        {
            root.then(trailCommand());
        }
        //?}

        root.then(Commands.literal("start")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .executes(context ->
                        {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(context, "name");
                            if (!ConfigNames.isValid(name))
                            {
                                Messages.fail(context.getSource(), "r 配置名无效（不能含 / \\ : * ? \" < > |）");
                                return 0;
                            }
                            PearlCalibration.startSession(player, name);
                            ServerConfigShare.broadcastNames(context.getSource().getServer());
                            return 1;
                        })));

        root.then(Commands.literal("stop")
                .executes(context ->
                {
                    PearlCalibration.stopSession(context.getSource().getPlayerOrException());
                    return 1;
                }));

        root.then(Commands.literal("list")
                .executes(context ->
                {
                    listConfigs(context.getSource());
                    return 1;
                }));

        root.then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .executes(context ->
                        {
                            String name = StringArgumentType.getString(context, "name");
                            if (PearlCannonConfigs.remove(name))
                            {
                                Messages.send(context.getSource(), "d CannonTool ", "g 已删除配置 ", "w " + name);
                                ServerConfigShare.broadcastNames(context.getSource().getServer());
                            }
                            else
                            {
                                Messages.fail(context.getSource(), "r 配置 " + name + " 不存在");
                            }
                            return 1;
                        })));

        root.then(Commands.literal("info")
                .executes(PearlCannonConfigCommands::info));

        root.then(Commands.literal("maxTnt")
                .then(Commands.argument("n", IntegerArgumentType.integer(0))
                        .executes(PearlCannonConfigCommands::setMaxTnt)));

        root.then(Commands.literal("resetTick")
                .executes(context ->
                {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = PearlCalibration.sessionName(player.getUUID());
                    if (name == null)
                    {
                        Messages.fail(context.getSource(), "r 请先执行 ", "g /cannon start ", "g <名称>");
                        return 0;
                    }
                    PearlCalibration.resetTick(player, name);
                    return 1;
                }));

        root.then(Commands.literal("selectTick")
                .then(Commands.argument("tick", IntegerArgumentType.integer(1))
                        .executes(context ->
                        {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PearlCalibration.selectTick(player, IntegerArgumentType.getInteger(context, "tick"));
                            return 1;
                        })));

        root.then(Commands.literal("give")
                .executes(context -> giveProjectiles(context, "ender_pearl"))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(GIVE_SUGGESTIONS)
                        .executes(context -> giveProjectiles(context, StringArgumentType.getString(context, "type")))));

        root.then(romCommand());

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> romCommand()
    {
        LiteralArgumentBuilder<CommandSourceStack> rom = Commands.literal("rom");

        rom.then(Commands.literal("order")
                .then(Commands.argument("order", StringArgumentType.word())
                        .suggests(ORDER_SUGGESTIONS)
                        .executes(PearlCannonConfigCommands::setRomOrder)));

        rom.then(Commands.literal("red")
                .then(Commands.argument("weights", StringArgumentType.greedyString())
                        .executes(context -> setRomWeights(context, true, false)))
                .then(Commands.literal("inverse")
                        .then(Commands.argument("weights", StringArgumentType.greedyString())
                                .executes(context -> setRomWeights(context, true, true)))));

        rom.then(Commands.literal("blue")
                .then(Commands.argument("weights", StringArgumentType.greedyString())
                        .executes(context -> setRomWeights(context, false, false)))
                .then(Commands.literal("inverse")
                        .then(Commands.argument("weights", StringArgumentType.greedyString())
                                .executes(context -> setRomWeights(context, false, true)))));

        rom.then(Commands.literal("show")
                .executes(context ->
                {
                    showRom(context.getSource());
                    return 1;
                }));

        return rom;
    }

    // ------------------------------------------------------------------ helpers

    private static Active active(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        String name = PearlCalibration.sessionName(player.getUUID());
        if (name == null)
        {
            Messages.fail(source, "r 请先执行 ", "g /cannon start ", "g <名称>");
            return null;
        }
        PearlCannonConfig config = PearlCannonConfigs.get(name);
        if (config == null)
        {
            Messages.fail(source, "r 配置 " + name + " 不存在");
            return null;
        }
        return new Active(name, config);
    }

    // ------------------------------------------------------------------ config

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        Active active = active(source);
        if (active == null) return 0;
        PearlCannonConfig config = active.config();

        Messages.send(source, "d CannonTool ", "g 配置 ", "w " + active.name());
        Messages.send(source, "g 维度 ", "w " + (config.dimension.isEmpty() ? "未知" : config.dimension),
                "g   原点 ", "w " + Format.vec(config.origin.toVec3()));
        Messages.send(source, "g 最大单侧药量 ", "w " + (config.maxTnt <= 0 ? "不限" : String.valueOf(config.maxTnt)));

        if (config.hasReference())
        {
            Messages.send(source, "g 基准 tick ", "w " + config.reference.tick);
            Messages.send(source, "g   相对位置 ", "w " + Format.vec(config.reference.pos.toVec3()));
            Messages.send(source, "g   冲量前动量 ", "w " + Format.vec(config.reference.motionBefore.toVec3()));
        }
        else
        {
            Messages.send(source, "g 基准 tick ", "r 未选择");
        }

        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            PearlCannonConfig.Sample blue = config.sample(direction + "B");
            PearlCannonConfig.Sample red = config.sample(direction + "R");
            Messages.send(source, "g  " + direction + "   ",
                    blue == null ? "f 蓝 未记录" : "c 蓝 " + Format.vec(blue.delta.toVec3()),
                    "g     ",
                    red == null ? "f 红 未记录" : "r 红 " + Format.vec(red.delta.toVec3()));
        }

        PearlCannonConfig.Sample empty = config.sample(PearlCannonConfig.EMPTY_KEY);
        if (empty == null)
        {
            Messages.send(source, "g  空配置 ", "f 未记录");
        }
        else
        {
            Messages.send(source, "g  空配置 ", "w Δv=" + Format.vec(empty.delta.toVec3()));
        }

        if (PearlCannonRom.configured(config.rom))
        {
            Messages.send(source, "g ROM 顺序 ", "w " + config.rom.order,
                    "g （", "w " + PearlCannonRom.orderDisplay(config.rom.order), "g ）");
            Messages.send(source, "r 红权重 ", "w " + PearlCannonRom.formatWeights(config.rom.red),
                    "g  共 ", "w " + PearlCannonRom.total(config.rom.red));
            Messages.send(source, "c 蓝权重 ", "w " + PearlCannonRom.formatWeights(config.rom.blue),
                    "g  共 ", "w " + PearlCannonRom.total(config.rom.blue));
        }
        else
        {
            Messages.send(source, "g ROM ", "f 未配置");
        }
        return 1;
    }

    private static int setMaxTnt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        Active active = active(source);
        if (active == null) return 0;

        int value = IntegerArgumentType.getInteger(context, "n");
        active.config().maxTnt = value;
        PearlCannonConfigs.save(active.name());
        Messages.send(source, "d CannonTool ", "g 最大单侧药量 = ", "w " + (value <= 0 ? "不限" : String.valueOf(value)));
        return 1;
    }

    private static int setRomOrder(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        Active active = active(source);
        if (active == null) return 0;

        String order = PearlCannonRom.normalizeOrder(StringArgumentType.getString(context, "order"));
        if (order == null)
        {
            Messages.fail(source, "r 顺序必须是 ", "g " + String.join("/", PearlCannonRom.ORDERS), "r 之一");
            return 0;
        }

        active.config().rom.order = order;
        PearlCannonConfigs.save(active.name());
        Messages.send(source, "d CannonTool ", "g ROM 顺序 = ", "w " + order,
                "g （", "w " + PearlCannonRom.orderDisplay(order), "g ）");
        return 1;
    }

    private static int setRomWeights(CommandContext<CommandSourceStack> context, boolean red, boolean inverse) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        Active active = active(source);
        if (active == null) return 0;

        List<Integer> weights;
        try
        {
            weights = PearlCannonRom.parseWeights(StringArgumentType.getString(context, "weights"));
        }
        catch (IllegalArgumentException e)
        {
            Messages.fail(source, "r " + e.getMessage());
            return 0;
        }
        if (inverse) weights = PearlCannonRom.reverse(weights);

        if (red) active.config().rom.red = weights;
        else active.config().rom.blue = weights;
        PearlCannonConfigs.save(active.name());

        Messages.send(source, "d CannonTool ", red ? "r 红权重 " : "c 蓝权重 ", "w " + PearlCannonRom.formatWeights(weights),
                "g  共 ", "w " + PearlCannonRom.total(weights),
                inverse ? "f  （inverse：已镜像保存）" : "");
        return 1;
    }

    private static void showRom(CommandSourceStack source) throws CommandSyntaxException
    {
        Active active = active(source);
        if (active == null) return;
        PearlCannonConfig config = active.config();

        Messages.send(source, "d CannonTool ", "g 配置 ", "w " + active.name(), "g  的 ROM");
        if (!PearlCannonRom.configured(config.rom))
        {
            Messages.send(source, "r 未配置完整：需要顺序 + 红权重 + 蓝权重");
            Messages.send(source, "g  /cannon rom order <" + String.join("|", PearlCannonRom.ORDERS) + ">");
            Messages.send(source, "g  /cannon rom red <w1,w2,...>");
            Messages.send(source, "g  /cannon rom blue <w1,w2,...>");
            return;
        }

        Messages.send(source, "g 顺序 ", "w " + config.rom.order,
                "g （", "w " + PearlCannonRom.orderDisplay(config.rom.order), "g ）");
        Messages.send(source, "r 红 ", "g 共 ", "w " + PearlCannonRom.total(config.rom.red),
                "g  ：", "w " + PearlCannonRom.formatWeights(config.rom.red));
        Messages.send(source, "c 蓝 ", "g 共 ", "w " + PearlCannonRom.total(config.rom.blue),
                "g  ：", "w " + PearlCannonRom.formatWeights(config.rom.blue));
    }

    private static int giveProjectiles(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Active active = active(source);
        if (active == null) return 0;
        String name = active.name();

        Item item;
        String label;
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.equals("ender_pearl"))
        {
            item = Items.ENDER_PEARL;
            label = "末影珍珠";
        }
        else if (normalized.equals("snowball"))
        {
            item = Items.SNOWBALL;
            label = "雪球";
        }
        else
        {
            Messages.fail(source, "r 类型必须是 ", "g ender_pearl", "r 或 ", "g snowball");
            return 0;
        }

        int count = 0;
        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            for (String color : PearlCannonConfig.COLORS)
            {
                String key = direction + color;
                ItemStack stack = new ItemStack(item);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name + ":" + key)
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA).withItalic(false)));
                if (!player.getInventory().add(stack))
                {
                    ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
                    player.level().addFreshEntity(dropped);
                }
                count++;
            }
        }

        Messages.send(source, "d CannonTool ", "g 已发放 ", "w " + count, "g  颗", "w " + label, "g （配置 ", "w " + name, "g ）");
        if (!active.config().hasReference())
        {
            Messages.send(source, "g 第一颗珍珠/雪球后选择基准 tick");
        }
        return 1;
    }

    private static void listConfigs(CommandSourceStack source)
    {
        List<String> names = PearlCannonConfigs.names();
        if (names.isEmpty())
        {
            Messages.send(source, "d CannonTool ", "g 还没有任何配置");
            return;
        }
        Messages.send(source, "d CannonTool ", "g 共 ", "w " + names.size(), "g  个配置：");
        for (String name : names)
        {
            PearlCannonConfig config = PearlCannonConfigs.get(name);
            String tick = config.hasReference() ? String.valueOf(config.reference.tick) : "未选";
            Messages.send(source, "g  - ", "w " + name,
                    "g   基准 tick ", "y " + tick,
                    "g   样本 ", "y " + config.samples.size(),
                    config.hasEmpty() ? "g   含空配置" : "");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> trailCommand()
    {
        return Commands.literal("trail")
                .executes(context -> trail(context, "off"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(TRAIL_SUGGESTIONS)
                        .executes(context -> trail(context, StringArgumentType.getString(context, "name"))));
    }

    private static int trail(CommandContext<CommandSourceStack> context, String value)
    {
        CommandSourceStack source = context.getSource();
        if (value.equalsIgnoreCase("off"))
        {
            ServerPearlTrail.clear();
            Messages.send(source, "d CannonTool ", "g 已关闭珍珠轨迹");
            return 1;
        }

        String filter = value.strip();
        if (filter.isEmpty())
        {
            Messages.fail(source, "r 名称不能为空");
            return 0;
        }

        ServerPearlTrail.setFilter(filter);
        Messages.send(source, "d CannonTool ", "g 珍珠轨迹过滤：", "w " + filter);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> calcCommand()
    {
        return Commands.literal("calc")
                .then(Commands.literal("normal")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CONFIG_SUGGESTIONS)
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(context -> calculate(context, false, 256, 0.0))
                                                .then(Commands.argument("maxTicks", IntegerArgumentType.integer(1, 2000))
                                                        .executes(context -> calculate(context, false,
                                                                IntegerArgumentType.getInteger(context, "maxTicks"), 0.0)))))))
                .then(Commands.literal("plane")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CONFIG_SUGGESTIONS)
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .executes(context -> calculate(context, true, 256,
                                                                DoubleArgumentType.getDouble(context, "y")))
                                                        .then(Commands.argument("maxTicks", IntegerArgumentType.integer(1, 2000))
                                                                .executes(context -> calculate(context, true,
                                                                        IntegerArgumentType.getInteger(context, "maxTicks"),
                                                                        DoubleArgumentType.getDouble(context, "y")))))))));
    }

    private static int calculate(CommandContext<CommandSourceStack> context, boolean plane, int maxTicks, double planeY)
    {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        double x = DoubleArgumentType.getDouble(context, "x");
        double z = DoubleArgumentType.getDouble(context, "z");

        PearlCannonConfig config = PearlCannonConfigs.get(name);
        if (config == null)
        {
            Messages.fail(source, "r 服务端没有配置 " + name);
            return 0;
        }
        if (!config.hasReference())
        {
            Messages.fail(source, "r 配置 " + name + " 还没有基准 tick");
            return 0;
        }

        List<PearlCannonSolver.Solution> results = plane
                ? PearlCannonSolver.solvePlane(config, x, z, planeY, config.maxTnt, maxTicks)
                : PearlCannonSolver.solve(config, x, z, config.maxTnt, maxTicks);

        if (results.isEmpty())
        {
            Messages.fail(source, "r 没有找到可行解");
            return 0;
        }

        Messages.send(source, "d CannonTool ", "g 计算 ", "w " + name, "g  → ",
                "w " + Format.f3(x) + ", " + Format.f3(z) + (plane ? "  y=" + Format.f3(planeY) : ""),
                "g   上限 ", "w " + maxTicks + " tick");

        int index = 1;
        for (PearlCannonSolver.Solution solution : results)
        {
            Messages.send(source, "g #" + index,
                    "w  方向 ", "w " + solution.group(),
                    "g  tick ", "w " + solution.ticks(),
                    "g  蓝 ", "w " + solution.blue(),
                    "g  红 ", "w " + solution.red(),
                    "g  落点 ", "w " + Format.vec(solution.end()),
                    "g  误差 ", "y " + Format.f3(solution.error()));
            index++;
        }
        return 1;
    }

    private static void sendHelp(CommandSourceStack source)
    {
        Messages.send(source, "d CannonTool ", "g 配置器");
        help(source, "/cannon calc normal <名称> <x> <z> [最大tick]", "服务端 tick 计算");
        help(source, "/cannon calc plane <名称> <x> <z> <y> [最大tick]", "服务端平面计算");
        //? =26.1 {
        if (FabricLoader.getInstance().isModLoaded("ryansserverrenderingkit"))
        {
            help(source, "/cannon trail <名称|off>", "服务端显示珍珠轨迹");
        }
        //?}
        help(source, "/cannon start <名称>", "开始配置");
        help(source, "/cannon stop", "结束配置");
        help(source, "/cannon list", "列出配置");
        help(source, "/cannon info", "查看当前配置");
        help(source, "/cannon delete <名称>", "删除配置");
        help(source, "/cannon maxTnt <数量>", "最大单侧药量");
        help(source, "/cannon resetTick", "清空基准与样本");
        help(source, "/cannon selectTick <tick>", "选择基准 tick");
        help(source, "/cannon give [ender_pearl|snowball]", "发放标定投掷物");
        help(source, "/cannon rom order <顺序>", "ROM 三段顺序");
        help(source, "/cannon rom red|blue <w1,w2,...>", "红/蓝权重");
        help(source, "/cannon rom red|blue inverse <w1,w2,...>", "红/蓝权重（镜像）");
        help(source, "/cannon rom show", "查看 ROM");
    }

    private static void help(CommandSourceStack source, String command, String description)
    {
        Messages.send(source, "g - " + command, "^ " + description);
    }
}
