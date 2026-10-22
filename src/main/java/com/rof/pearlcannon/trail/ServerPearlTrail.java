package com.rof.pearlcannon.trail;

//? =26.1 {
import com.rof.pearlcannon.PearlCannon;
import ml.mypals.ryansserverrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansserverrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansserverrenderingkit.shapeManagers.ShapeManagers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
//?}

/**
 * 服务端珍珠轨迹：每个玩家最多一条，逐 tick 画线段，珍珠消失后保留已画轨迹。
 * <p>每个 tick 的位移单独建一段，最多保留最近 1000 段（1000 tick）；段数上限只影响
 * 历史长度，不会一次性预创建全部 display。</p>
 */
public final class ServerPearlTrail
{
    //? =26.1 {
    private static final int MAX_SEGMENTS = 1000;
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private static String filter;
    private static boolean initialized;

    private ServerPearlTrail() {}

    public static void init()
    {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(ServerPearlTrail::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static void setFilter(String value)
    {
        filter = value == null || value.isBlank() ? null : value.strip();
        clearTrails();
    }

    public static String filter()
    {
        return filter;
    }

    public static void clear()
    {
        filter = null;
        clearTrails();
    }

    private static void onServerTick(MinecraftServer server)
    {
        if (filter == null) return;

        Map<UUID, ThrownEnderpearl> active = new HashMap<>();
        for (ServerLevel level : server.getAllLevels())
        {
            for (Entity entity : level.getAllEntities())
            {
                if (!(entity instanceof ThrownEnderpearl pearl)) continue;
                if (!matches(pearlName(pearl))) continue;
                if (!(pearl.getOwner() instanceof ServerPlayer player)) continue;

                active.merge(player.getUUID(), pearl,
                        (first, second) -> first.getId() >= second.getId() ? first : second);
            }
        }

        Set<UUID> activePearls = new HashSet<>();
        for (Map.Entry<UUID, ThrownEnderpearl> entry : active.entrySet())
        {
            UUID playerId = entry.getKey();
            ThrownEnderpearl pearl = entry.getValue();
            activePearls.add(pearl.getUUID());

            Trail trail = TRAILS.get(playerId);
            if (trail == null || !pearl.getUUID().equals(trail.pearlId))
            {
                if (trail != null) trail.remove();
                trail = new Trail(playerId, pearl);
                TRAILS.put(playerId, trail);
            }

            trail.add(pearl.position());
        }

        // 珍珠实体消失后只冻结轨迹，不删除已经画出的路径。
        for (Trail trail : TRAILS.values())
        {
            if (trail.pearlId != null && !activePearls.contains(trail.pearlId))
            {
                trail.freeze();
            }
        }
    }

    private static void clearTrails()
    {
        for (Trail trail : TRAILS.values()) trail.remove();
        TRAILS.clear();
    }

    private static String pearlName(ThrownEnderpearl pearl)
    {
        Component custom = pearl.getCustomName();
        if (custom != null) return custom.getString();
        return pearl.getItem().getHoverName().getString();
    }

    private static boolean matches(String name)
    {
        if (filter == null || name == null || name.isEmpty()) return false;
        return name.equals(filter)
                || name.startsWith(filter + ":")
                || name.endsWith(":" + filter);
    }

    private static Color colorFor(ThrownEnderpearl pearl)
    {
        int hash = pearlName(pearl).hashCode();
        Color base = Color.getHSBColor((hash & 0xFFFF) / 65535.0f, 0.7f, 1.0f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 220);
    }

    private static final class Trail
    {
        private final UUID playerId;
        private final ServerLevel level;
        private final Color color;
        private final List<Segment> segments = new ArrayList<>();
        private UUID pearlId;
        private Vec3 lastPosition;
        private int sequence;

        private Trail(UUID playerId, ThrownEnderpearl pearl)
        {
            this.playerId = playerId;
            this.level = (ServerLevel) pearl.level();
            this.color = colorFor(pearl);
            this.pearlId = pearl.getUUID();
            this.lastPosition = pearl.position();
        }

        private void add(Vec3 position)
        {
            if (lastPosition == null)
            {
                lastPosition = position;
                return;
            }
            if (lastPosition.distanceToSqr(position) < 1.0E-6)
            {
                return;
            }

            segments.add(new Segment(level, playerId, sequence++, lastPosition, position, color));
            if (segments.size() > MAX_SEGMENTS)
            {
                segments.removeFirst().remove();
            }
            lastPosition = position;
        }

        private void freeze()
        {
            pearlId = null;
        }

        private void remove()
        {
            for (Segment segment : segments) segment.remove();
            segments.clear();
        }
    }

    private static final class Segment
    {
        private final Identifier id;
        private final StripLineShape shape;

        private Segment(ServerLevel level, UUID playerId, int sequence, Vec3 start, Vec3 end, Color color)
        {
            id = Identifier.fromNamespaceAndPath(PearlCannon.MOD_ID, "trail/" + playerId + "/" + sequence);
            shape = ShapeGenerator.generateStripLine()
                    .level(level)
                    .vertexes(List.of(start, end))
                    .lineWidth(0.08f)
                    .color(color)
                    .seeThrough(true)
                    .build();
            ShapeManagers.addShape(id, shape);
        }

        private void remove()
        {
            ShapeManagers.removeShape(id);
        }
    }
    //?} else {
    /*private ServerPearlTrail() {}

    public static void init() {}

    public static void setFilter(String value) {}

    public static String filter() { return null; }

    public static void clear() {}
    *///?}
}
