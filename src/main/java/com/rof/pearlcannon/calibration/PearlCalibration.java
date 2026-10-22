package com.rof.pearlcannon.calibration;

import com.rof.pearlcannon.config.PearlCannonConfig;
import com.rof.pearlcannon.config.PearlCannonConfigs;
import com.rof.pearlcannon.config.PearlCannonRom;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 珍珠/雪球标定：记录以“配置名:样本名”命名的投掷物，逐 tick 采样它在自身 tick 之外的动量变化。
 * <p>Δv(t) = v_开头(t) − v_末尾(t−1)。第一颗投掷物用于选择基准 tick，之后每颗都在基准 tick 末被清理。</p>
 */
public final class PearlCalibration
{
    public static final int MAX_TRACK_TICKS = 400;
    private static final double EPSILON_SQR = 1.0E-12;
    private static final int MAX_SELECTION_ROWS = 16;

    private static final Map<UUID, String> SESSIONS = new HashMap<>();
    private static final Map<UUID, Trace> ACTIVE = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private PearlCalibration() {}

    public static void resetState()
    {
        SESSIONS.clear();
        ACTIVE.clear();
        PENDING.clear();
    }

    public record TickSample(int tick, Vec3 pos, Vec3 motionBefore, Vec3 motionAfter, Vec3 delta) {}

    private record Pending(String configName, String key, String dimension, Vec3 origin, List<TickSample> samples) {}

    public static void startSession(ServerPlayer player, String configName)
    {
        PearlCannonConfig config = PearlCannonConfigs.getOrCreate(configName);
        if (config.dimension == null || config.dimension.isEmpty())
        {
            //? >=1.21.11 {
            config.dimension = player.level().dimension().identifier().toString();
            //?} else {
            /*config.dimension = player.level().dimension().location().toString();
            *///?}
            PearlCannonConfigs.save(configName);
        }
        SESSIONS.put(player.getUUID(), configName);
        PENDING.remove(player.getUUID());

        player.sendSystemMessage(header("开始配置 " + configName));
        player.sendSystemMessage(Component.literal("投掷命名为 " + configName + ":样本名 的珍珠/雪球（00B~11R，ZZ 为空配置）")
                .withStyle(ChatFormatting.GRAY));
        if (!config.hasReference())
        {
            player.sendSystemMessage(Component.literal("第一颗珍珠/雪球后选择基准 tick").withStyle(ChatFormatting.GOLD));
        }
        sendProgress(player, configName);
    }

    public static void stopSession(ServerPlayer player)
    {
        String configName = SESSIONS.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        if (configName == null)
        {
            player.sendSystemMessage(warn("当前没有进行中的配置"));
            return;
        }
        player.sendSystemMessage(header("已结束配置 " + configName));
    }

    public static String sessionName(UUID player)
    {
        return SESSIONS.get(player);
    }

    /** 由 Mixin 在投掷物第一次 tick 时调用；不跟踪时返回 null。 */
    public static Trace begin(ThrowableProjectile projectile)
    {
        if (!(projectile instanceof ThrowableItemProjectile itemProjectile)) return null;

        Entity owner = itemProjectile.getOwner();
        if (!(owner instanceof ServerPlayer player)) return null;
        UUID playerId = player.getUUID();
        for (Trace active : ACTIVE.values())
        {
            if (active.ownerId.equals(playerId)) return null;
        }

        String configName = SESSIONS.get(playerId);
        if (configName == null) return null;

        String name = pearlName(itemProjectile);
        if (name == null) return null;
        String key = PearlCannonConfig.sampleKeyOf(name, configName);
        if (key == null)
        {
            if (name.startsWith(configName + ":") || name.startsWith(configName + "："))
            {
                player.sendSystemMessage(warn("无法识别的样本名: " + name));
            }
            return null;
        }

        //? >=1.21.11 {
        String dimension = itemProjectile.level().dimension().identifier().toString();
        //?} else {
        /*String dimension = itemProjectile.level().dimension().location().toString();
        *///?}

        PearlCannonConfig config = PearlCannonConfigs.get(configName);
        if (config != null && config.dimension != null && !config.dimension.isEmpty()
                && !config.dimension.equals(dimension))
        {
            player.sendSystemMessage(warn("配置 " + configName + " 维度不一致（" + dimension + "），已忽略"));
            return null;
        }

        Trace trace = new Trace(itemProjectile, itemProjectile.level().getServer(), playerId, configName, key,
                player.position(), dimension);
        ACTIVE.put(itemProjectile.getUUID(), trace);
        return trace;
    }

    public static boolean hasTrace(ThrowableProjectile projectile)
    {
        return ACTIVE.containsKey(projectile.getUUID());
    }

    public static void tickHead(ThrowableProjectile projectile, Vec3 position, Vec3 motion)
    {
        Trace trace = ACTIVE.get(projectile.getUUID());
        if (trace != null) trace.onTickHead(position, motion);
    }

    public static boolean tickEnd(ThrowableProjectile projectile, Vec3 motion)
    {
        Trace trace = ACTIVE.get(projectile.getUUID());
        return trace != null && trace.onTickEnd(motion);
    }

    /** 命中时结束标定；返回 true 表示已取消原版命中处理。 */
    public static boolean onHit(ThrowableItemProjectile projectile)
    {
        Trace trace = ACTIVE.get(projectile.getUUID());
        if (trace == null) return false;
        trace.finish();
        if (!projectile.isRemoved()) projectile.discard();
        return true;
    }

    public static void onServerTick(MinecraftServer server)
    {
        if (ACTIVE.isEmpty()) return;
        for (Trace trace : new ArrayList<>(ACTIVE.values()))
        {
            if (trace.projectile.isRemoved()) trace.finish();
        }
    }

    public static void selectTick(ServerPlayer player, int tick)
    {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null)
        {
            player.sendSystemMessage(warn("当前没有等待选择的测量"));
            return;
        }

        TickSample sample = null;
        for (TickSample candidate : pending.samples())
        {
            if (candidate.tick() == tick)
            {
                sample = candidate;
                break;
            }
        }
        if (sample == null)
        {
            player.sendSystemMessage(warn("该弹射物在 tick " + tick + " 没有动量变化"));
            return;
        }

        PearlCannonConfig config = PearlCannonConfigs.get(pending.configName());
        if (config == null)
        {
            player.sendSystemMessage(warn("配置 " + pending.configName() + " 已不存在"));
            PENDING.remove(player.getUUID());
            return;
        }

        config.dimension = pending.dimension();
        config.origin = PearlCannonConfig.Vec.of(pending.origin());
        config.reference.tick = tick;
        config.reference.pos = PearlCannonConfig.Vec.of(sample.pos());
        config.reference.motionBefore = PearlCannonConfig.Vec.of(sample.motionBefore());
        config.samples.put(pending.key(), toConfigSample(sample));
        PearlCannonConfigs.save(pending.configName());
        PENDING.remove(player.getUUID());

        player.sendSystemMessage(ok("基准 tick = " + tick + "，已记录 " + pending.key() + " 样本"));
        sendProgress(player, pending.configName());
    }

    public static void sendProgress(ServerPlayer player, String configName)
    {
        PearlCannonConfig config = PearlCannonConfigs.get(configName);
        if (config == null)
        {
            player.sendSystemMessage(warn("配置 " + configName + " 不存在"));
            return;
        }

        player.sendSystemMessage(header("配置 " + configName
                + " · tick " + (config.hasReference() ? String.valueOf(config.reference.tick) : "未选")));
        player.sendSystemMessage(Component.literal("  药量 " + (config.maxTnt <= 0 ? "不限" : String.valueOf(config.maxTnt))
                        + " · ROM " + (PearlCannonRom.configured(config.rom) ? config.rom.order : "未配置"))
                .withStyle(ChatFormatting.GRAY));

        List<String> missing = new ArrayList<>();
        List<MutableComponent> statusLines = new ArrayList<>();
        MutableComponent status = null;
        for (int index = 0; index < PearlCannonConfig.DIRECTIONS.length; index++)
        {
            if (index % 2 == 0)
            {
                status = Component.literal("  ").withStyle(ChatFormatting.GRAY);
                statusLines.add(status);
            }
            else
            {
                status.append(Component.literal("    ").withStyle(ChatFormatting.GRAY));
            }

            String direction = PearlCannonConfig.DIRECTIONS[index];
            status.append(Component.literal(direction + " ").withStyle(ChatFormatting.GRAY));
            for (String color : PearlCannonConfig.COLORS)
            {
                String key = direction + color;
                boolean present = config.hasSample(key);
                if (!present) missing.add(key);
                status.append(Component.literal((color.equals("B") ? "蓝" : "红") + (present ? "✔ " : "✘ "))
                        .withStyle(present ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        for (MutableComponent line : statusLines) player.sendSystemMessage(line);

        player.sendSystemMessage(Component.literal("  空 ZZ " + (config.hasEmpty() ? "✔" : "✘"))
                .withStyle(config.hasEmpty() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        if (!missing.isEmpty())
        {
            player.sendSystemMessage(Component.literal("  还缺 " + String.join(" ", missing)).withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void resetTick(ServerPlayer player, String configName)
    {
        PearlCannonConfig config = PearlCannonConfigs.get(configName);
        if (config == null)
        {
            player.sendSystemMessage(warn("配置 " + configName + " 不存在"));
            return;
        }
        config.reference = new PearlCannonConfig.Reference();
        config.samples.clear();
        PearlCannonConfigs.save(configName);
        player.sendSystemMessage(ok("已清空 " + configName + " 的基准 tick 与样本"));
    }

    private static PearlCannonConfig.Sample toConfigSample(TickSample sample)
    {
        PearlCannonConfig.Sample result = new PearlCannonConfig.Sample();
        result.delta = PearlCannonConfig.Vec.of(sample.delta());
        result.pos = PearlCannonConfig.Vec.of(sample.pos());
        result.motionAfter = PearlCannonConfig.Vec.of(sample.motionAfter());
        return result;
    }

    public static boolean hasName(ThrowableProjectile projectile)
    {
        return projectile instanceof ThrowableItemProjectile itemProjectile && pearlName(itemProjectile) != null;
    }

    private static String pearlName(ThrowableItemProjectile projectile)
    {
        Component custom = projectile.getCustomName();
        if (custom != null) return custom.getString();
        ItemStack item = projectile.getItem();
        if (item != null && !item.isEmpty() && item.getCustomName() != null) return item.getCustomName().getString();
        return null;
    }

    private static MutableComponent header(String text)
    {
        return Component.literal("[CannonTool] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    private static MutableComponent ok(String text)
    {
        return Component.literal("[CannonTool] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private static MutableComponent warn(String text)
    {
        return Component.literal("[CannonTool] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static void send(MinecraftServer server, UUID playerId, Component message)
    {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendSystemMessage(message);
    }

    public static final class Trace
    {
        private final ThrowableItemProjectile projectile;
        private final MinecraftServer server;
        private final UUID ownerId;
        private final String configName;
        private final String key;
        private final String dimension;
        private final Vec3 origin;
        private final List<TickSample> samples = new ArrayList<>();

        private int tick;
        private Vec3 lastEnd;
        private Vec3 pendingPos;
        private Vec3 pendingBefore;
        private Vec3 pendingAfter;
        private boolean applied;
        private boolean finished;

        private Trace(ThrowableItemProjectile projectile, MinecraftServer server, UUID ownerId, String configName,
                      String key, Vec3 origin, String dimension)
        {
            this.projectile = projectile;
            this.server = server;
            this.ownerId = ownerId;
            this.configName = configName;
            this.key = key;
            this.origin = origin;
            this.dimension = dimension;
        }

        public void onTickHead(Vec3 position, Vec3 motion)
        {
            if (finished) return;
            tick++;

            if (lastEnd == null)
            {
                lastEnd = motion;
                pendingBefore = motion;
                pendingAfter = motion;
                pendingPos = position.subtract(origin);
                if (tick >= MAX_TRACK_TICKS) finish();
                return;
            }

            pendingPos = position.subtract(origin);
            pendingBefore = lastEnd;
            pendingAfter = motion;

            Vec3 delta = motion.subtract(lastEnd);
            if (horizontalChanged(delta))
            {
                samples.add(new TickSample(tick, pendingPos, pendingBefore, pendingAfter, delta));
            }
            lastEnd = null;
            if (tick >= MAX_TRACK_TICKS) finish();
        }

        /** 返回 true 表示应在本 tick 末尾清理珍珠。 */
        public boolean onTickEnd(Vec3 motion)
        {
            if (finished) return false;
            lastEnd = motion;

            PearlCannonConfig config = PearlCannonConfigs.get(configName);
            if (config == null) return false;
            if (config.hasReference() && tick == config.reference.tick)
            {
                applyReferenceTickSample(config);
                finish();
                return true;
            }
            return false;
        }

        public void finish()
        {
            if (finished) return;
            finished = true;
            ACTIVE.remove(projectile.getUUID());
            if (applied) return;

            PearlCannonConfig config = PearlCannonConfigs.get(configName);
            if (config == null)
            {
                send(server, ownerId, warn("配置 " + configName + " 不存在，测量已丢弃"));
                return;
            }

            if (!config.hasReference())
            {
                if (samples.isEmpty())
                {
                    send(server, ownerId, warn("未检测到动量变化"));
                    return;
                }
                boolean replaced = PENDING.put(ownerId,
                        new Pending(configName, key, dimension, origin, new ArrayList<>(samples))) != null;
                if (replaced)
                {
                    send(server, ownerId, Component.literal("[CannonTool] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("已替换上一次待选记录").withStyle(ChatFormatting.YELLOW)));
                }
                sendSelectionList();
                return;
            }

            send(server, ownerId, warn("弹射物在 tick " + config.reference.tick
                    + " 前消失（最后 tick " + tick + "）"));
        }

        private void applyReferenceTickSample(PearlCannonConfig config)
        {
            Vec3 delta = pendingAfter.subtract(pendingBefore);
            if (!horizontalChanged(delta))
            {
                applied = true;
                send(server, ownerId, warn("基准 tick " + config.reference.tick
                        + " 未检测到 " + key + " 的动量变化"
                        + anomalyTicks()));
                return;
            }

            PearlCannonConfig.Sample sample = new PearlCannonConfig.Sample();
            sample.delta = PearlCannonConfig.Vec.of(delta);
            sample.pos = PearlCannonConfig.Vec.of(pendingPos);
            sample.motionAfter = PearlCannonConfig.Vec.of(pendingAfter);
            config.samples.put(key, sample);
            PearlCannonConfigs.save(configName);
            applied = true;

            ServerPlayer player = player();
            if (player == null) return;
            player.sendSystemMessage(ok("已记录 " + key + "  Δv=" + formatVec(delta)));
            sendProgress(player, configName);
        }

        private String anomalyTicks()
        {
            if (samples.isEmpty()) return "；无动量变化";
            StringBuilder builder = new StringBuilder("；变化在 tick ");
            for (int i = 0; i < samples.size(); i++)
            {
                if (i > 0) builder.append(", ");
                builder.append(samples.get(i).tick());
                if (i >= 9)
                {
                    builder.append(" ...");
                    break;
                }
            }
            return builder.toString();
        }

        private void sendSelectionList()
        {
            ServerPlayer player = player();
            if (player == null) return;

            player.sendSystemMessage(header("选择基准 tick · " + configName));
            player.sendSystemMessage(Component.literal(key + " 的动量变化（XZ），点击选择：")
                    .withStyle(ChatFormatting.GRAY));

            int shown = 0;
            for (TickSample sample : samples)
            {
                if (shown >= MAX_SELECTION_ROWS)
                {
                    player.sendSystemMessage(Component.literal("  ... 还有 " + (samples.size() - shown) + " 条未显示")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                shown++;
                MutableComponent line = Component.literal(String.format(Locale.ROOT,
                                "  #%d  Δv=%s  |Δv_xz|=%.4f", sample.tick(), formatVec(sample.delta()), horizontalLength(sample.delta())))
                        .withStyle(style -> style
                                .withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new ClickEvent.RunCommand("/cannon selectTick " + sample.tick()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击设为基准 tick"))));
                player.sendSystemMessage(line);
            }
        }

        private ServerPlayer player()
        {
            return server == null ? null : server.getPlayerList().getPlayer(ownerId);
        }
    }

    // 基准 tick 的判定只看水平方向：只在 Y 轴上的动量变化不计入候选
    private static boolean horizontalChanged(Vec3 delta)
    {
        return delta.x * delta.x + delta.z * delta.z > EPSILON_SQR;
    }

    private static double horizontalLength(Vec3 vec)
    {
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private static String formatVec(Vec3 vec)
    {
        return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", vec.x, vec.y, vec.z);
    }
}
