package com.rof.pearlcannon.client;

//? =26.1 {
import com.rof.pearlcannon.PearlCannon;
import ml.mypals.ryansrenderingkit.builders.shapeBuilders.ShapeGenerator;
import ml.mypals.ryansrenderingkit.shape.Shape;
import ml.mypals.ryansrenderingkit.shape.line.StripLineShape;
import ml.mypals.ryansrenderingkit.shapeManagers.ShapeManagers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * 按名称过滤末影珍珠，并用 Ryan's Rendering Kit 画出飞行轨迹。
 * <p>名称取实体自定义名，没有时取投掷物物品的自定义名；支持完整名称、配置名前缀和样本名后缀。</p>
 */
public final class PearlTrailRenderer
{
    //? =26.1 {
    private static final int MAX_POINTS = 2048;
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private static String filter;
    private static boolean initialized;

    private PearlTrailRenderer() {}

    public static void init()
    {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(PearlTrailRenderer::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
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

    private static void tick(Minecraft client)
    {
        if (client.level == null)
        {
            clearTrails();
            return;
        }
        if (filter == null || ShapeManagers.managers.isEmpty()) return;

        Set<UUID> live = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering())
        {
            if (!(entity instanceof ThrownEnderpearl pearl)) continue;
            if (!matches(pearlName(pearl))) continue;

            live.add(pearl.getUUID());
            Trail trail = TRAILS.get(pearl.getUUID());
            if (trail == null)
            {
                trail = new Trail(pearl);
                TRAILS.put(pearl.getUUID(), trail);
            }
            trail.add(pearl.position());
        }

        for (UUID id : new ArrayList<>(TRAILS.keySet()))
        {
            if (live.contains(id)) continue;
            TRAILS.remove(id).remove();
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
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 200);
    }

    private static final class Trail
    {
        private final Identifier id;
        private final StripLineShape shape;
        private final List<Vec3> points = new ArrayList<>();

        private Trail(ThrownEnderpearl pearl)
        {
            id = Identifier.fromNamespaceAndPath(PearlCannon.MOD_ID, "trail/" + pearl.getUUID());
            Vec3 start = pearl.position();
            points.add(start);
            points.add(start.add(0, 0.01, 0));
            shape = ShapeGenerator.generateStripLine()
                    .vertexes(points)
                    .lineWidth(2.0f)
                    .color(colorFor(pearl))
                    .seeThrough(true)
                    .build(Shape.RenderingType.BATCH);
            ShapeManagers.addShape(id, shape);
        }

        private void add(Vec3 position)
        {
            Vec3 last = points.get(points.size() - 1);
            if (last.distanceToSqr(position) < 0.0001) return;
            if (points.size() >= MAX_POINTS) points.remove(0);
            points.add(position);
            shape.setVertexes(points);
        }

        private void remove()
        {
            ShapeManagers.removeShape(id);
        }
    }
    //?} else {
    /*private PearlTrailRenderer() {}

    public static void init() {}

    public static void setFilter(String value) {}

    public static String filter() { return null; }

    public static void clear() {}
    *///?}
}
