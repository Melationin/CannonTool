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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
//?}

/**
 * 服务端珍珠轨迹：按名称过滤末影珍珠，用 Ryan's Server Rendering Kit 的虚拟 display 画线。
 * <p>固定顶点数，避免每 tick 增删 display；轨迹是最近一段飞行路径。</p>
 */
public final class ServerPearlTrail
{
    //? =26.1 {
    private static final int MAX_POINTS = 48;
    private static final int MAX_TRAILS = 8;
    private static final Map<UUID, Trail> TRAILS = new LinkedHashMap<>();

    private static String filter;
    private static boolean initialized;
    private static int tick;

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
        tick = 0;
    }

    private static void onServerTick(MinecraftServer server)
    {
        if (filter == null) return;
        tick++;

        Set<UUID> live = new HashSet<>();
        for (ServerLevel level : server.getAllLevels())
        {
            for (Entity entity : level.getAllEntities())
            {
                if (!(entity instanceof ThrownEnderpearl pearl)) continue;
                if (!matches(pearlName(pearl))) continue;

                live.add(pearl.getUUID());
                Trail trail = TRAILS.get(pearl.getUUID());
                if (trail == null)
                {
                    trail = new Trail(pearl);
                    TRAILS.put(pearl.getUUID(), trail);
                    trimTrails();
                }
                if (tick % 2 == 0) trail.add(pearl.position());
            }
        }

        for (UUID id : new ArrayList<>(TRAILS.keySet()))
        {
            if (live.contains(id)) continue;
            TRAILS.remove(id).remove();
        }
    }

    private static void trimTrails()
    {
        while (TRAILS.size() > MAX_TRAILS)
        {
            Iterator<UUID> iterator = TRAILS.keySet().iterator();
            if (!iterator.hasNext()) return;
            UUID id = iterator.next();
            Trail trail = TRAILS.get(id);
            iterator.remove();
            if (trail != null) trail.remove();
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
        private final Identifier id;
        private final StripLineShape shape;
        private final List<Vec3> points = new ArrayList<>(MAX_POINTS);
        private int filled = 1;

        private Trail(ThrownEnderpearl pearl)
        {
            id = Identifier.fromNamespaceAndPath(PearlCannon.MOD_ID, "trail/" + pearl.getUUID());
            Vec3 start = pearl.position();
            for (int i = 0; i < MAX_POINTS; i++)
            {
                points.add(start.add(0, i * 0.001, 0));
            }

            shape = ShapeGenerator.generateStripLine()
                    .level((ServerLevel) pearl.level())
                    .vertexes(points)
                    .lineWidth(0.08f)
                    .color(colorFor(pearl))
                    .seeThrough(true)
                    .build();
            ShapeManagers.addShape(id, shape);
        }

        private void add(Vec3 position)
        {
            if (filled < MAX_POINTS)
            {
                points.set(filled, position);
                filled++;
                for (int i = filled; i < MAX_POINTS; i++)
                {
                    points.set(i, position.add(0, (i - filled + 1) * 0.001, 0));
                }
            }
            else
            {
                for (int i = 1; i < MAX_POINTS; i++)
                {
                    points.set(i - 1, points.get(i));
                }
                points.set(MAX_POINTS - 1, position);
            }
            shape.setVertexes(points);
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
