/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.runtime.arrow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import pit12.runtime.session.ClientSession;
import pit12.shared.event.ClientEvents;
import pit12.shared.lifecycle.ClientLifecycle;

public final class ArrowTracker implements ClientLifecycle, ArrowPacketObserver, ArrowObserver {
    private final Runnable onTickListener = this::onTick;

    private final Minecraft minecraft;
    private final ClientSession session;
    private final List<ArrowListener> listeners = new ArrayList<>();
    private final ArrowHitTracker hits;
    private final Map<Integer, TrackedArrow> arrows = new HashMap<>();
    private final Map<Integer, ShooterState> shooters = new HashMap<>();
    private final Deque<ReleasedBow> releases = new ArrayDeque<>();
    private final Runnable sessionChanged = this::refresh;
    private NetHandlerPlayClient connection;
    private WorldClient world;
    private boolean started;
    private boolean trackingHits;

    public ArrowTracker(Minecraft minecraft, ClientSession session) {
        this.minecraft = minecraft;
        this.session = session;
        hits = new ArrowHitTracker(minecraft, listeners);
    }

    @Override
    public void start() {
        session.checkThread();
        if (started)
            return;
        started = true;
        updateHitTracking();
        session.addListener(sessionChanged);
        refresh();
    }

    @Override
    public void stop() {
        session.checkThread();
        started = false;
        updateHitTracking();
        session.removeListener(sessionChanged);
        unbind();
        clear();
        listeners.clear();
        world = null;
    }

    public void addListener(ArrowListener listener) {
        session.checkThread();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            updateHitTracking();
            refresh();
        }
    }

    public void removeListener(ArrowListener listener) {
        session.checkThread();
        listeners.remove(listener);
        hits.removeListener(listener);
        updateHitTracking();
        Iterator<TrackedArrow> iterator = arrows.values().iterator();
        while (iterator.hasNext()) {
            TrackedArrow tracked = iterator.next();
            tracked.listeners.remove(listener);
            if (tracked.listeners.isEmpty() && !trackingHits) {
                ((ArrowAccess) tracked.arrow).pit12$bindArrowObserver(null);
                iterator.remove();
            }
        }
        if (listeners.isEmpty()) {
            unbind();
            clear();
        }
    }

    private void updateHitTracking() {
        boolean enabled = false;
        if (started) {
            for (ArrowListener listener : listeners) {
                if (listener.hitWindowNanos() >= 0) {
                    enabled = true;
                    break;
                }
            }
        }
        if (trackingHits == enabled)
            return;
        trackingHits = enabled;
        if (trackingHits) {
            ClientEvents.TICK_END.add(onTickListener);
        } else {
            ClientEvents.TICK_END.remove(onTickListener);
            hits.clear();
        }
    }

    private void refresh() {
        WorldClient nextWorld = session.world();
        if (world != nextWorld) {
            clear();
            world = nextWorld;
        }
        NetHandlerPlayClient next = started && !listeners.isEmpty() ? session.connection() : null;
        if (connection != next) {
            unbind();
            connection = next;
            if (connection != null) {
                ((ArrowPacketBinding) connection).bindArrowPacketObserver(this);
            }
        }
    }

    private void unbind() {
        if (connection != null) {
            ((ArrowPacketBinding) connection).bindArrowPacketObserver(null);
            connection = null;
        }
    }

    private void clear() {
        for (TrackedArrow tracked : arrows.values()) {
            ((ArrowAccess) tracked.arrow).pit12$bindArrowObserver(null);
        }
        arrows.clear();
        hits.clear();
        shooters.clear();
        releases.clear();
        for (ArrowListener listener : listeners) {
            listener.onArrowsReset();
        }
    }

    @Override
    public void onArrowSpawn(int entityId) {
        session.checkThread();
        session.refresh();
        if (world == null || listeners.isEmpty())
            return;
        Entity entity = world.getEntityByID(entityId);
        if (!(entity instanceof EntityArrow))
            return;
        EntityArrow arrow = (EntityArrow) entity;
        TrackedArrow tracked = observeSpawn(arrow);
        if (tracked != null) {
            arrows.put(entityId, tracked);
            ((ArrowAccess) arrow).pit12$bindArrowObserver(this);
        }
    }

    private TrackedArrow observeSpawn(EntityArrow arrow) {
        TrackedArrow tracked = null;
        if (trackingHits) {
            hits.spawned(arrow);
            tracked = new TrackedArrow(arrow);
        }
        if (!(arrow.shootingEntity instanceof EntityPlayer))
            return tracked;
        EntityPlayer shooter = (EntityPlayer) arrow.shootingEntity;
        long now = System.nanoTime();
        ShooterState state = shooters.get(shooter.getEntityId());
        ItemStack bow = shooter.getHeldItem();
        boolean sneaking = shooter.isSneaking();
        long lastSneaking = state == null ? 0 : state.lastSneaking;
        if (shooter == minecraft.thePlayer) {
            while (!releases.isEmpty() && now - releases.peekFirst().at > 1_000_000_000L) {
                releases.removeFirst();
            }
            ReleasedBow release = releases.pollFirst();
            if (release != null) {
                bow = release.bow;
                sneaking = release.sneaking;
                lastSneaking = 0;
            }
        }
        if ((bow == null || !(bow.getItem() instanceof ItemBow)) && state != null
                && now - state.bowChangedAt <= 250_000_000L) {
            bow = state.bow;
        }
        if (bow == null || !(bow.getItem() instanceof ItemBow))
            return tracked;
        for (ArrowListener listener : listeners) {
            if (listener.onArrowSpawn(arrow, shooter, bow, sneaking, lastSneaking)) {
                if (tracked == null)
                    tracked = new TrackedArrow(arrow);
                tracked.listeners.add(listener);
            }
        }
        return tracked;
    }

    @Override
    public void beforeEntityMetadata(int entityId, List<WatchableObject> metadata) {
        if (world == null)
            return;
        if (metadata != null)
            hits.metadata(entityId, metadata);
        Entity entity = world.getEntityByID(entityId);
        if (entity instanceof EntityPlayer && entity.isSneaking()) {
            shooters.computeIfAbsent(entityId, id -> new ShooterState()).lastSneaking =
                    System.nanoTime();
        }
    }

    @Override
    public void beforeHeldItemChange(int entityId) {
        if (world == null)
            return;
        Entity entity = world.getEntityByID(entityId);
        if (!(entity instanceof EntityPlayer))
            return;
        ItemStack bow = ((EntityPlayer) entity).getHeldItem();
        if (bow != null && bow.getItem() instanceof ItemBow) {
            ShooterState state = shooters.computeIfAbsent(entityId, id -> new ShooterState());
            state.bow = bow.copy();
            state.bowChangedAt = System.nanoTime();
        }
    }

    @Override
    public void onLocalBowRelease() {
        EntityPlayer player = minecraft.thePlayer;
        if (player == null)
            return;
        ItemStack bow = player.getHeldItem();
        if (bow != null && bow.getItem() instanceof ItemBow) {
            long now = System.nanoTime();
            while (!releases.isEmpty() && now - releases.peekFirst().at > 1_000_000_000L) {
                releases.removeFirst();
            }
            releases.addLast(new ReleasedBow(bow.copy(), player.isSneaking(), now));
        }
    }

    @Override
    public void onLocalStopSneaking() {
        if (minecraft.thePlayer != null) {
            shooters.computeIfAbsent(minecraft.thePlayer.getEntityId(),
                    id -> new ShooterState()).lastSneaking = System.nanoTime();
        }
    }

    @Override
    public void onShooterTeleport(int entityId, double x, double y, double z) {
        if (world == null)
            return;
        Entity entity = world.getEntityByID(entityId);
        if (!(entity instanceof EntityPlayer))
            return;
        EntityPlayer player = (EntityPlayer) entity;
        // Remote players interpolate toward serverPos, so compare server coordinates.
        double dx = x - (player == minecraft.thePlayer ? player.posX : player.serverPosX / 32.0);
        double dy = y - (player == minecraft.thePlayer ? player.posY : player.serverPosY / 32.0);
        double dz = z - (player == minecraft.thePlayer ? player.posZ : player.serverPosZ / 32.0);
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        for (ArrowListener listener : listeners) {
            listener.onShooterTeleport(player, distanceSquared);
        }
    }

    @Override
    public void onEntityRemoved(int entityId) {
        shooters.remove(entityId);
        remove(entityId, true);
        hits.serverRemoved(entityId);
    }

    @Override
    public void onPlayerHurt(EntityPlayer player) {
        hits.hurt(player);
    }

    @Override
    public void onArrowCorrected(int entityId, boolean velocityChanged) {
        TrackedArrow tracked = arrows.get(entityId);
        if (tracked != null) {
            for (ArrowListener listener : tracked.listeners) {
                listener.onArrowCorrected(tracked.arrow, velocityChanged);
            }
        }
    }

    @Override
    public void onArrowHitCandidate(EntityArrow arrow, Entity target) {
        hits.collided(arrow, target);
    }

    @Override
    public void onArrowUpdated(EntityArrow arrow) {
        hits.updated(arrow);
        if (arrow.isDead || ((ArrowAccess) arrow).pit12$isInGround()) {
            remove(arrow.getEntityId(), false);
        }
    }

    private void onTick() {
        hits.tick();
    }

    @Override
    public void onArrowUnloaded(EntityArrow arrow) {
        remove(arrow.getEntityId(), false);
    }

    @Override
    public void onBlockChanged(BlockPos position) {
        if (arrows.isEmpty())
            return;
        for (ArrowListener listener : listeners) {
            listener.onBlockChanged(position);
        }
    }

    private void remove(int entityId, boolean serverRemoved) {
        TrackedArrow tracked = arrows.remove(entityId);
        if (tracked == null)
            return;
        ((ArrowAccess) tracked.arrow).pit12$bindArrowObserver(null);
        for (ArrowListener listener : tracked.listeners) {
            listener.onArrowRemoved(tracked.arrow, serverRemoved);
        }
        hits.removed(tracked.arrow, serverRemoved);
    }

    private static final class TrackedArrow {
        final EntityArrow arrow;
        final List<ArrowListener> listeners = new ArrayList<>(2);

        TrackedArrow(EntityArrow arrow) {
            this.arrow = arrow;
        }
    }
    private static final class ShooterState {
        ItemStack bow;
        long bowChangedAt;
        long lastSneaking;
    }
    private static final class ReleasedBow {
        final ItemStack bow;
        final boolean sneaking;
        final long at;

        ReleasedBow(ItemStack bow, boolean sneaking, long at) {
            this.bow = bow;
            this.sneaking = sneaking;
            this.at = at;
        }
    }
}
