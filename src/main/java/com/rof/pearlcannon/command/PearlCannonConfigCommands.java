package com.rof.pearlcannon.command;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** 服务端珍珠炮配置命令：标定、药量上限与 ROM。计算在客户端执行。 */
public final class PearlCannonConfigCommands
{
    private static final SuggestionProvider<CommandSourceStack> CONFIG_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PearlCannonConfigs.names(), builder);

    private static final SuggestionProvider<CommandSourceStack> ORDER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PearlCannonRom.ORDERS, builder);

    private record Active(String name, PearlCannonConfig config) {}

    private PearlCannonConfigCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(rootCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rootCommand()
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pearlCannonConfig")
                .requires(CommandAccess::canUse)
                .executes(context ->
                {
                    sendHelp(context.getSource());
                    return 1;
                });

        root.then(Commands.literal("start")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CONFIG_SUGGESTIONS)
                        .executes(context ->
                        {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(context, "name");
                            if (!ConfigNames.isValid(name))
                            {
                                Messages.fail(context.getSource(), "r 配置名不能包含 / \\ : * ? \" < > | 等字符，"
                                        + "也不能是 . 或系统保留名");
                                return 0;
                            }
                            PearlCalibration.startSession(player, name);
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
                                Messages.send(context.getSource(), "d 珍珠炮 ", "g 已删除配置 ", "w " + name);
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
                        Messages.fail(context.getSource(), "r 请先执行 ", "y /pearlCannonConfig start <名称>");
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
                .executes(PearlCannonConfigCommands::givePearls));

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
            Messages.fail(source, "r 请先执行 ", "y /pearlCannonConfig start <名称>");
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

        Messages.send(source, "d 珍珠炮 ", "g 配置 ", "w " + active.name());
        Messages.send(source, "g 维度 ", "w " + (config.dimension.isEmpty() ? "未知" : config.dimension),
                "g   原点 ", "w " + Format.vec(config.origin.toVec3()),
                "g   最大单侧药量 ", "w " + (config.maxTnt <= 0 ? "不限" : String.valueOf(config.maxTnt)));

        if (config.hasReference())
        {
            Messages.send(source, "g 基准 tick ", "w " + config.reference.tick,
                    "g   相对位置 ", "w " + Format.vec(config.reference.pos.toVec3()),
                    "g   冲量前动量 ", "w " + Format.vec(config.reference.motionBefore.toVec3()),
                    "g   冲量后动量 ", "w " + Format.vec(config.reference.motionAfter.toVec3()));
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
            Messages.send(source, "g  空配置 ", "f 未记录（默认冲量为 0）");
        }
        else
        {
            Messages.send(source, "g  空配置 ", "w Δv=" + Format.vec(empty.delta.toVec3()),
                    "g    冲量后动量 ", "w " + Format.vec(empty.motionAfter.toVec3()));
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
        Messages.send(source, "d 珍珠炮 ", "g 最大单侧药量 = ", "w " + (value <= 0 ? "不限" : String.valueOf(value)));
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
            Messages.fail(source, "r 顺序必须是 ", "y " + String.join("/", PearlCannonRom.ORDERS), "r 之一");
            return 0;
        }

        active.config().rom.order = order;
        PearlCannonConfigs.save(active.name());
        Messages.send(source, "d 珍珠炮 ", "g ROM 顺序 = ", "w " + order,
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

        Messages.send(source, "d 珍珠炮 ", red ? "r 红权重 " : "c 蓝权重 ", "w " + PearlCannonRom.formatWeights(weights),
                "g  共 ", "w " + PearlCannonRom.total(weights),
                inverse ? "f  （inverse：已镜像保存）" : "");
        return 1;
    }

    private static void showRom(CommandSourceStack source) throws CommandSyntaxException
    {
        Active active = active(source);
        if (active == null) return;
        PearlCannonConfig config = active.config();

        Messages.send(source, "d 珍珠炮 ", "g 配置 ", "w " + active.name(), "g  的 ROM");
        if (!PearlCannonRom.configured(config.rom))
        {
            Messages.send(source, "r 未配置完整：需要顺序 + 红权重 + 蓝权重");
            Messages.send(source, "g  /pearlCannonConfig rom order ", "y <" + String.join("|", PearlCannonRom.ORDERS) + ">");
            Messages.send(source, "g  /pearlCannonConfig rom red ", "y <w1,w2,...>");
            Messages.send(source, "g  /pearlCannonConfig rom blue ", "y <w1,w2,...>");
            return;
        }

        Messages.send(source, "g 顺序 ", "w " + config.rom.order,
                "g （", "w " + PearlCannonRom.orderDisplay(config.rom.order), "g ）");
        Messages.send(source, "r 红 ", "g 共 ", "w " + PearlCannonRom.total(config.rom.red),
                "g  ：", "w " + PearlCannonRom.formatWeights(config.rom.red));
        Messages.send(source, "c 蓝 ", "g 共 ", "w " + PearlCannonRom.total(config.rom.blue),
                "g  ：", "w " + PearlCannonRom.formatWeights(config.rom.blue));
    }

    private static int givePearls(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Active active = active(source);
        if (active == null) return 0;
        String name = active.name();

        int count = 0;
        for (String direction : PearlCannonConfig.DIRECTIONS)
        {
            for (String color : PearlCannonConfig.COLORS)
            {
                String key = direction + color;
                ItemStack stack = new ItemStack(Items.ENDER_PEARL);
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

        Messages.send(source, "d 珍珠炮 ", "g 已发放 ", "w " + count, "g  颗标定珍珠（配置 ", "w " + name, "g ）");
        if (!active.config().hasReference())
        {
            Messages.send(source, "g 第一颗珍珠结束后需要在聊天栏选择一个基准 tick");
        }
        return 1;
    }

    private static void listConfigs(CommandSourceStack source)
    {
        List<String> names = PearlCannonConfigs.names();
        if (names.isEmpty())
        {
            Messages.send(source, "d 珍珠炮 ", "g 还没有任何配置");
            return;
        }
        Messages.send(source, "d 珍珠炮 ", "g 共 ", "w " + names.size(), "g  个配置：");
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

    private static void sendHelp(CommandSourceStack source)
    {
        Messages.send(source, "d 珍珠炮 ", "g 服务端配置");
        Messages.send(source, "g  /pearlCannonConfig start ", "y <名称>", "g  开始配置");
        Messages.send(source, "g  /pearlCannonConfig stop", "g  / ", "y list", "g  / ", "y info", "g  / ", "y delete <名称>");
        Messages.send(source, "g  /pearlCannonConfig maxTnt ", "y <数量>", "g  设置最大单侧药量");
        Messages.send(source, "g  /pearlCannonConfig rom order ", "y <RBD|RDB|BRD|BDR|DRB|DBR>");
        Messages.send(source, "g  /pearlCannonConfig rom red|blue ", "y <w1,w2,...>");
        Messages.send(source, "g  /pearlCannonConfig rom red|blue inverse ", "y <w1,w2,...>");
        Messages.send(source, "g  /pearlCannonConfig rom show", "g  / ", "y give", "g  / ", "y resetTick");
        Messages.send(source, "f  样本名: 00B 00R 01B 01R 10B 10R 11B 11R；空配置: ZZ");
        Messages.send(source, "g  配置保存在存档目录的 pearlcannon/ ，一炮一个 json");
        Messages.send(source, "g  计算在客户端执行：", "y /pearlCannonCalc normal ...", "g  或 ", "y /pearlCannonCalc pull <名称>");
    }
}
