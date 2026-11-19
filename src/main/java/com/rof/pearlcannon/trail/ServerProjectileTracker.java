package com.rof.pearlcannon.trail;

import com.mojang.math.Transformation;
import ml.mypals.ryansserverrenderingkit.display.DisplayTransformHelper;
import ml.mypals.ryansserverrenderingkit.display.VirtualDisplay;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端投掷物追踪：按玩家保存最后投出的珍珠/雪球，提供 HUD 与可复用 display 轨迹。
 * <p>轨迹记录原始路径；渲染时只对主人 256 格内可见部分动态切小段并分配 display 池，
 * 每个 tick 的位置另用不带描边的白色混凝土方块 display 标记（大小与碰撞箱一致）。
 * 珍珠/雪球 UUID 每 tick 重试，进入未加载区块也不会结束追踪。</p>
 */
public final class ServerProjectileTracker
{
    private static final int MAX_SEGMENTS = 1000;
    private static final int MAX_POOL = 256;
    private static final double VIEW_RADIUS = 256.0;
    private static final double VIEW_RADIUS_SQR = VIEW_RADIUS * VIEW_RADIUS;
    private static final double CHUNK_LENGTH = 16.0;

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private static boolean initialized;
    private static MinecraftServer lastTickServer;
    private static int lastTick = Integer.MIN_VALUE;

    private ServerProjectileTracker() {}

    public static void init()
    {
        if (initialized) return;
        initialized = true;
        // 同时挂服务端 tick 和 level tick：tick freeze 时仍保留一个可用的刷新入口；
        // 用 tickCount 去重，保证同一 tick 只更新一次。
        ServerTickEvents.END_SERVER_TICK.register(ServerProjectileTracker::runOnce);
        //? >=26.1 {
        ServerTickEvents.END_LEVEL_TICK.register(level -> runOnce(level.getServer()));
        //?}
        ServerLifecycleEvents.SERVER_STARTED.register(TrackerSettings::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
        {
            clearAll();
            TrackerSettings.onServerStopping();
            lastTickServer = null;
            lastTick = Integer.MIN_VALUE;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        {
            ServerPlayer player = handler.getPlayer();
            if (player != null && TrackerSettings.load(player.getUUID()).isAlways())
            {
                tracker(player.getUUID());
            }
        });
    }

    /** 只追踪当前最后一颗投掷物；下一颗投出后自动关闭。 */
    public static boolean trackOnce(ServerPlayer player)
    {
        ThrowableItemProjectile latest = latest(player.level().getServer(), player.getUUID());
        if (latest == null) return false;

        Tracker tracker = tracker(player.getUUID());
        tracker.mode = Mode.ONCE;
        tracker.lastSeenId = latest.getUUID();
        tracker.track(latest, false);
        tracker.save();
        return true;
    }

    /** 持续追踪之后投出的每一颗珍珠/雪球。 */
    public static void trackAlways(ServerPlayer player)
    {
        Tracker tracker = tracker(player.getUUID());
        ThrowableItemProjectile latest = latest(player.level().getServer(), player.getUUID());
        tracker.mode = Mode.ALWAYS;

        if (latest != null)
        {
            tracker.lastSeenId = latest.getUUID();
            tracker.track(latest, false);
        }
        else
        {
            tracker.trackedId = null;
            tracker.lastSeenId = null;
            tracker.trail.clear();
        }
        tracker.save();
    }

    public static void stop(ServerPlayer player)
    {
        Tracker tracker = tracker(player.getUUID());
        tracker.mode = Mode.OFF;
        tracker.trackedId = null;
        tracker.frozen = false;
        tracker.trail.clear();
        tracker.clearHud(player);
        tracker.save();
    }

    public static boolean toggleHud(ServerPlayer player, Boolean value)
    {
        Tracker tracker = tracker(player.getUUID());
        boolean enabled = value != null ? value : !tracker.hud;
        tracker.hud = enabled;
        if (!enabled) tracker.clearHud(player);
        tracker.save();
        return enabled;
    }

    public static boolean toggleTrail(ServerPlayer player, Boolean value)
    {
        Tracker tracker = tracker(player.getUUID());
        boolean enabled = value != null ? value : !tracker.trailEnabled;
        tracker.trailEnabled = enabled;
        tracker.trail.update(player, enabled, tracker.boxEnabled);
        tracker.save();
        return enabled;
    }

    public static boolean toggleBox(ServerPlayer player, Boolean value)
    {
        Tracker tracker = tracker(player.getUUID());
        boolean enabled = value != null ? value : !tracker.boxEnabled;
        tracker.boxEnabled = enabled;
        tracker.trail.update(player, tracker.trailEnabled, enabled);
        tracker.save();
        return enabled;
    }

    private static Tracker tracker(UUID ownerId)
    {
        return TRACKERS.computeIfAbsent(ownerId, Tracker::new);
    }

    private static ThrowableItemProjectile latest(MinecraftServer server, UUID ownerId)
    {
        ThrowableItemProjectile latest = null;
        for (ServerLevel level : server.getAllLevels())
        {
            for (Entity entity : level.getAllEntities())
            {
                if (!(entity instanceof ThrowableItemProjectile projectile)) continue;
                if (!isTrackedProjectile(projectile)) continue;
                if (projectile.isRemoved()) continue;
                if (!(projectile.getOwner() instanceof ServerPlayer player)) continue;
                if (!player.getUUID().equals(ownerId)) continue;
                if (latest == null || projectile.getId() > latest.getId()) latest = projectile;
            }
        }
        return latest;
    }

    private static Entity findEntity(MinecraftServer server, UUID trackedId)
    {
        for (ServerLevel level : server.getAllLevels())
        {
            Entity entity = level.getEntity(trackedId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void runOnce(MinecraftServer server)
    {
        if (server == null) return;
        int tick = server.getTickCount();
        if (server == lastTickServer && tick == lastTick) return;
        lastTickServer = server;
        lastTick = tick;
        onServerTick(server);
    }

    private static void onServerTick(MinecraftServer server)
    {
        if (TRACKERS.isEmpty()) return;

        Map<UUID, ThrowableItemProjectile> newestByPlayer = new HashMap<>();

        for (ServerLevel level : server.getAllLevels())
        {
            for (Entity entity : level.getAllEntities())
            {
                if (!(entity instanceof ThrowableItemProjectile projectile)) continue;
                if (!isTrackedProjectile(projectile)) continue;
                if (projectile.isRemoved()) continue;

                if (!(projectile.getOwner() instanceof ServerPlayer player)) continue;

                newestByPlayer.merge(player.getUUID(), projectile,
                        (first, second) -> first.getId() >= second.getId() ? first : second);
            }
        }

        for (Map.Entry<UUID, ThrowableItemProjectile> entry : newestByPlayer.entrySet())
        {
            Tracker tracker = tracker(entry.getKey());
            ThrowableItemProjectile projectile = entry.getValue();
            UUID newestId = projectile.getUUID();

            if (!newestId.equals(tracker.lastSeenId))
            {
                if (tracker.mode == Mode.ONCE)
                {
                    tracker.mode = Mode.OFF;
                    tracker.trackedId = null;
                    tracker.frozen = false;
                    tracker.trail.clear();
                    tracker.save();
                }
                else if (tracker.mode == Mode.ALWAYS)
                {
                    tracker.track(projectile, true);
                }
                tracker.lastSeenId = newestId;
            }
            else if (tracker.mode == Mode.ALWAYS && tracker.trackedId == null)
            {
                tracker.track(projectile, true);
            }
        }

        for (Tracker tracker : TRACKERS.values())
        {
            tracker.tick(server);
        }
    }

    private static void clearAll()
    {
        for (Tracker tracker : TRACKERS.values()) tracker.trail.remove();
        TRACKERS.clear();
    }

    private static boolean isTrackedProjectile(ThrowableItemProjectile projectile)
    {
        return projectile instanceof ThrownEnderpearl || projectile instanceof Snowball;
    }

    private static Color colorFor(ThrowableItemProjectile projectile)
    {
        int hash = projectile.getUUID().hashCode();
        Color base = Color.getHSBColor(Math.floorMod(hash, 360) / 360.0f, 0.7f, 1.0f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 220);
    }

    private static float yawOf(Vec3 direction)
    {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static float pitchOf(Vec3 direction)
    {
        return (float) Math.toDegrees(-Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
    }

    private static Mode loadMode(String name)
    {
        return "ALWAYS".equals(name) ? Mode.ALWAYS : Mode.OFF;
    }

    private enum Mode
    {
        OFF,
        ONCE,
        ALWAYS
    }

    private static final class Tracker
    {
        private final UUID ownerId;
        private final TrackedTrail trail;
        private Mode mode = Mode.OFF;
        private UUID trackedId;
        private UUID lastSeenId;
        private boolean hud = true;
        private boolean trailEnabled = true;
        private boolean boxEnabled = false;
        private boolean hudVisible;
        private boolean frozen;

        private Tracker(UUID ownerId)
        {
            this.ownerId = ownerId;
            this.trail = new TrackedTrail(ownerId);

            TrackerSettings settings = TrackerSettings.load(ownerId);
            this.mode = loadMode(settings.mode);
            this.hud = settings.hud;
            this.trailEnabled = settings.trail;
            this.boxEnabled = settings.box;
        }

        private void save()
        {
            TrackerSettings.save(ownerId, mode == Mode.ALWAYS ? "ALWAYS" : "OFF",
                    hud, trailEnabled, boxEnabled);
        }

        private void track(ThrowableItemProjectile projectile, boolean fromTickStart)
        {
            trackedId = projectile.getUUID();
            frozen = false;
            trail.reset(projectile, fromTickStart);
        }

        private void tick(MinecraftServer server)
        {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);

            if (mode == Mode.OFF || trackedId == null)
            {
                frozen = false;
                clearHud(owner);
                trail.update(owner, false, false);
                return;
            }

            Entity entity = findEntity(server, trackedId);
            ThrowableItemProjectile projectile =
                    entity instanceof ThrowableItemProjectile tracked && !tracked.isRemoved() ? tracked : null;

            if (projectile != null)
            {
                frozen = false;
                ServerLevel level = (ServerLevel) projectile.level();
                if (!trail.setLevel(level, projectile.position()))
                {
                    trail.add(projectile.position());
                }

                if (hud) showHud(owner, projectile.position());
                else clearHud(owner);
            }
            else if (!frozen)
            {
                // 珍珠消失后轨迹冻结：不再追加点，但下面仍每 tick 更新 display 池的可见范围。
                frozen = true;
                clearHud(owner);
            }

            trail.update(owner, trailEnabled, boxEnabled);
        }

        private void showHud(ServerPlayer owner, Vec3 position)
        {
            if (owner == null)
            {
                hudVisible = false;
                return;
            }

            String text = String.format(Locale.ROOT, "[%.4f,%.4f,%.4f]",
                    position.x, position.y, position.z);
            Component message = Component.literal("Pos:").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(text).withStyle(ChatFormatting.BLUE));
            owner.sendSystemMessage(message, true);
            hudVisible = true;
        }

        private void clearHud(ServerPlayer owner)
        {
            if (!hudVisible) return;
            if (owner != null) owner.sendSystemMessage(Component.empty(), true);
            hudVisible = false;
        }
    }

    private static final class TrackedTrail
    {
        private final UUID ownerId;
        private final List<TrackSegment> segments = new ArrayList<>();
        private final Map<ServerLevel, TrailPool> pools = new HashMap<>();
        private ServerLevel level;
        private Vec3 lastPosition;
        private Color color = Color.WHITE;
        private Vector3f markerCenter = new Vector3f();
        private Transformation markerTransform = DisplayTransformHelper.identity();
        private long sequence;

        private TrackedTrail(UUID ownerId)
        {
            this.ownerId = ownerId;
        }

        private void reset(ThrowableItemProjectile projectile, boolean fromTickStart)
        {
            hideAll();
            segments.clear();
            level = (ServerLevel) projectile.level();
            // 首次发现时珍珠已经 tick 过一次，用 tick 起始位置补上第一段。
            lastPosition = fromTickStart && projectile.tickCount > 0
                    ? projectile.oldPosition()
                    : projectile.position();
            color = colorFor(projectile);
            MarkerGeometry marker = computeMarkerGeometry(projectile);
            markerCenter = marker.center();
            markerTransform = marker.transform();

            if (fromTickStart && projectile.tickCount > 0)
            {
                add(projectile.position());
            }
        }

        private static MarkerGeometry computeMarkerGeometry(ThrowableItemProjectile projectile)
        {
            AABB box = projectile.getBoundingBox();
            Vec3 position = projectile.position();
            float sizeX = (float) box.getXsize();
            float sizeY = (float) box.getYsize();
            float sizeZ = (float) box.getZsize();
            Vector3f center = new Vector3f(
                    (float) ((box.minX + box.maxX) * 0.5 - position.x),
                    (float) ((box.minY + box.maxY) * 0.5 - position.y),
                    (float) ((box.minZ + box.maxZ) * 0.5 - position.z));
            Transformation transform = DisplayTransformHelper.of(
                    new Vector3f(-sizeX * 0.5f, -sizeY * 0.5f, -sizeZ * 0.5f),
                    new Quaternionf(),
                    new Vector3f(sizeX, sizeY, sizeZ));
            return new MarkerGeometry(center, transform);
        }

        private void clear()
        {
            hideAll();
            segments.clear();
        }

        private boolean setLevel(ServerLevel level, Vec3 position)
        {
            if (this.level == level) return false;
            this.level = level;
            this.lastPosition = position;
            return true;
        }

        private void add(Vec3 position)
        {
            if (lastPosition == null)
            {
                lastPosition = position;
                return;
            }
            if (lastPosition.distanceToSqr(position) < 1.0E-6) return;

            addSegment(new TrackSegment(sequence++, level, lastPosition, position, color));
            lastPosition = position;
        }

        private void addSegment(TrackSegment segment)
        {
            segments.add(segment);
            pools.computeIfAbsent(segment.level, poolLevel -> new TrailPool(poolLevel, ownerId));
            while (segments.size() > MAX_SEGMENTS)
            {
                segments.removeFirst();
            }
        }

        private void update(ServerPlayer owner, boolean linesEnabled, boolean boxesEnabled)
        {
            if (owner == null)
            {
                hideAll();
                return;
            }

            ServerLevel ownerLevel = (ServerLevel) owner.level();
            for (TrailPool pool : pools.values())
            {
                if (pool.level != ownerLevel)
                {
                    pool.hideAll();
                    continue;
                }
                pool.update(segments, owner, linesEnabled, boxesEnabled, markerCenter, markerTransform);
            }
        }

        private void hideAll()
        {
            for (TrailPool pool : pools.values()) pool.hideAll();
        }

        private void remove()
        {
            for (TrailPool pool : pools.values()) pool.remove();
            pools.clear();
            segments.clear();
        }
    }

    private record TrackSegment(long id, ServerLevel level, Vec3 start, Vec3 end, Color color) {}

    private record MarkerGeometry(Vector3f center, Transformation transform) {}

    private record ChunkKey(long segmentId, int index) {}

    private record Chunk(ChunkKey key, Vec3 start, Vec3 end, Color color)
    {
        private Vec3 midpoint()
        {
            return start.add(end).scale(0.5);
        }

        private Transformation transform()
        {
            // 线段模型沿局部 +Z，display 实体的 yaw/pitch 负责把 +Z 转到 start → end。
            double length = start.distanceTo(end);
            return DisplayTransformHelper.of(
                    new Vector3f(-0.04f, -0.04f, (float) (-length * 0.5)),
                    new Quaternionf(),
                    new Vector3f(0.08f, 0.08f, (float) length));
        }

        private float yaw()
        {
            return yawOf(end.subtract(start));
        }

        private float pitch()
        {
            return pitchOf(end.subtract(start));
        }
    }

    private record VisibleChunk(Chunk chunk, double distance) {}

    private record VisibleMarker(TrackSegment segment, double distance) {}

    private static final class TrailPool
    {
        private final ServerLevel level;
        private final UUID ownerId;
        private final List<Slot> slots = new ArrayList<>();
        private final List<MarkerSlot> markers = new ArrayList<>();

        private TrailPool(ServerLevel level, UUID ownerId)
        {
            this.level = level;
            this.ownerId = ownerId;
        }

        private void update(List<TrackSegment> segments, ServerPlayer owner, boolean linesEnabled,
                            boolean boxesEnabled, Vector3f markerCenter, Transformation markerTransform)
        {
            if (linesEnabled)
            {
                List<VisibleChunk> visible = new ArrayList<>();
                for (TrackSegment segment : segments)
                {
                    if (segment.level != level) continue;
                    addVisibleChunks(segment, owner, visible);
                }

                visible.sort(Comparator.comparingDouble(VisibleChunk::distance));
                if (visible.size() > MAX_POOL)
                {
                    visible = new ArrayList<>(visible.subList(0, MAX_POOL));
                }

                Set<ChunkKey> wanted = new HashSet<>();
                for (VisibleChunk entry : visible) wanted.add(entry.chunk().key());

                for (Slot slot : slots)
                {
                    if (slot.assigned == null) continue;
                    if (wanted.remove(slot.assigned)) slot.keep();
                    else slot.hide();
                }

                for (VisibleChunk entry : visible)
                {
                    Chunk chunk = entry.chunk();
                    if (!wanted.remove(chunk.key())) continue;

                    Slot slot = freeSlot();
                    if (slot == null) slot = createSlot();
                    if (slot == null) break;
                    slot.assign(chunk, ownerId);
                }
            }
            else
            {
                hideSlots();
            }

            if (boxesEnabled) updateMarkers(segments, owner, markerCenter, markerTransform);
            else hideMarkers();
        }

        private void updateMarkers(List<TrackSegment> segments, ServerPlayer owner,
                                   Vector3f markerCenter, Transformation markerTransform)
        {
            List<VisibleMarker> visible = new ArrayList<>();
            for (TrackSegment segment : segments)
            {
                if (segment.level != level) continue;
                double distanceSq = segment.end.distanceToSqr(owner.position());
                if (distanceSq > VIEW_RADIUS_SQR) continue;
                visible.add(new VisibleMarker(segment, distanceSq));
            }

            visible.sort(Comparator.comparingDouble(VisibleMarker::distance));
            if (visible.size() > MAX_POOL)
            {
                visible = new ArrayList<>(visible.subList(0, MAX_POOL));
            }

            Set<Long> wanted = new HashSet<>();
            for (VisibleMarker entry : visible) wanted.add(entry.segment().id());

            for (MarkerSlot marker : markers)
            {
                if (marker.assigned == null) continue;
                if (wanted.remove(marker.assigned)) marker.keep();
                else marker.hide();
            }

            for (VisibleMarker entry : visible)
            {
                TrackSegment segment = entry.segment();
                if (!wanted.remove(segment.id())) continue;

                MarkerSlot marker = freeMarker();
                if (marker == null) marker = createMarker();
                if (marker == null) break;
                marker.assign(segment, ownerId, markerCenter, markerTransform);
            }
        }

        private void addVisibleChunks(TrackSegment segment, ServerPlayer owner, List<VisibleChunk> out)
        {
            Vec3 start = segment.start;
            Vec3 delta = segment.end.subtract(start);
            double length = delta.length();
            if (length < 1.0E-3) return;

            Vec3 toOwner = owner.position().subtract(start);
            double t = toOwner.dot(delta) / (length * length);
            t = Math.max(0.0, Math.min(1.0, t));

            Vec3 closest = start.add(delta.scale(t));
            double distanceSq = closest.distanceToSqr(owner.position());
            if (distanceSq > VIEW_RADIUS_SQR) return;

            double half = Math.sqrt(Math.max(0.0, VIEW_RADIUS_SQR - distanceSq));
            double center = t * length;
            double from = Math.max(0.0, center - half - CHUNK_LENGTH);
            double to = Math.min(length, center + half + CHUNK_LENGTH);

            int count = Math.max(1, (int) Math.ceil(length / CHUNK_LENGTH));
            int first = Math.max(0, Math.min(count - 1, (int) Math.floor(from / CHUNK_LENGTH)));
            int last = Math.max(0, Math.min(count - 1, (int) Math.ceil(to / CHUNK_LENGTH)));

            for (int index = first; index <= last; index++)
            {
                double d0 = index * CHUNK_LENGTH;
                double d1 = Math.min(length, d0 + CHUNK_LENGTH);
                Vec3 a = start.add(delta.scale(d0 / length));
                Vec3 b = start.add(delta.scale(d1 / length));
                Vec3 midpoint = a.add(b).scale(0.5);
                double midpointDistanceSq = midpoint.distanceToSqr(owner.position());
                if (midpointDistanceSq > VIEW_RADIUS_SQR) continue;

                Chunk chunk = new Chunk(new ChunkKey(segment.id, index), a, b, segment.color);
                out.add(new VisibleChunk(chunk, midpointDistanceSq));
            }
        }

        private Slot freeSlot()
        {
            for (Slot slot : slots)
            {
                if (slot.assigned == null) return slot;
            }
            return null;
        }

        private Slot createSlot()
        {
            if (slots.size() >= MAX_POOL) return null;
            Slot slot = new Slot(level);
            slots.add(slot);
            return slot;
        }

        private MarkerSlot freeMarker()
        {
            for (MarkerSlot marker : markers)
            {
                if (marker.assigned == null) return marker;
            }
            return null;
        }

        private MarkerSlot createMarker()
        {
            if (markers.size() >= MAX_POOL) return null;
            MarkerSlot marker = new MarkerSlot(level);
            markers.add(marker);
            return marker;
        }

        private void hideSlots()
        {
            for (Slot slot : slots)
            {
                if (slot.assigned != null) slot.hide();
            }
        }

        private void hideMarkers()
        {
            for (MarkerSlot marker : markers)
            {
                if (marker.assigned != null) marker.hide();
            }
        }

        private void hideAll()
        {
            hideSlots();
            hideMarkers();
        }

        private void remove()
        {
            for (Slot slot : slots) slot.remove();
            slots.clear();
            for (MarkerSlot marker : markers) marker.remove();
            markers.clear();
        }
    }

    private static final class Slot
    {
        private final VirtualDisplay display;
        private ChunkKey assigned;

        private Slot(ServerLevel level)
        {
            // FIXED：朝向由实体 yaw/pitch + 线段局部 +Z 决定，不随玩家视角旋转。
            display = VirtualDisplay.block(level, 0, -1000, 0, VirtualDisplay.DEFAULT_BLOCK_STATE)
                    .billboard(Display.BillboardConstraints.FIXED)
                    .bright()
                    .seeThrough(true, 0xFFFFFFFF)
                    .filter(player -> false);
            display.sync();
        }

        private void keep()
        {
            display.sync();
        }

        private void hide()
        {
            // remove() also clears the kit's viewer set; the slot can still be reused by assign().
            display.remove();
            assigned = null;
        }

        private void assign(Chunk chunk, UUID ownerId)
        {
            hide();

            Transformation transform = chunk.transform();
            if (transform == null) return;

            Vec3 midpoint = chunk.midpoint();
            display.pos(midpoint.x, midpoint.y, midpoint.z);
            display.yawPitch(chunk.yaw(), chunk.pitch());
            display.transform(transform);
            display.seeThrough(true, chunk.color().getRGB());
            display.filter(player -> player.getUUID().equals(ownerId));
            display.sync();
            assigned = chunk.key();
        }

        private void remove()
        {
            display.remove();
            assigned = null;
        }
    }

    private static final class MarkerSlot
    {
        private final VirtualDisplay display;
        private Long assigned;

        private MarkerSlot(ServerLevel level)
        {
            // 白色混凝土方块标记：大小和偏移由碰撞箱 transformation 决定，不调用 seeThrough 以避免描边。
            //? >=26.2 {
            /*BlockState markerBlock = Blocks.CONCRETE.white().defaultBlockState();
            *///?} else {
            BlockState markerBlock = Blocks.WHITE_CONCRETE.defaultBlockState();
            //?}
            display = VirtualDisplay.block(level, 0, -1000, 0, markerBlock)
                    .billboard(Display.BillboardConstraints.FIXED)
                    .bright()
                    .filter(player -> false);
            display.sync();
        }

        private void keep()
        {
            display.sync();
        }

        private void hide()
        {
            // remove() also clears the kit's viewer set; the slot can still be reused by assign().
            display.remove();
            assigned = null;
        }

        private void assign(TrackSegment segment, UUID ownerId,
                            Vector3f markerCenter, Transformation markerTransform)
        {
            hide();

            Vec3 end = segment.end;
            Vec3 direction = end.subtract(segment.start);
            display.pos(end.x + markerCenter.x, end.y + markerCenter.y, end.z + markerCenter.z);
            display.yawPitch(yawOf(direction), pitchOf(direction));
            display.transform(markerTransform);
            display.filter(player -> player.getUUID().equals(ownerId));
            display.sync();
            assigned = segment.id();
        }

        private void remove()
        {
            display.remove();
            assigned = null;
        }
    }
}
