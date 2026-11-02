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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import pit12.shared.performance.HotPath;

final class ArrowHitTracker {
    private final Minecraft minecraft;
    private final List<ArrowListener> listeners;
    private final Map<Integer, Shot> shots = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private long batch;

    ArrowHitTracker(Minecraft minecraft, List<ArrowListener> listeners) {
        this.minecraft = minecraft;
        this.listeners = listeners;
    }

    private long window() {
        // Collect with the longer window, then match each arrow with its shooter's window.
        long window = 0;
        for (int index = 0, size = listeners.size(); index < size; index++)
            window = Math.max(window, listeners.get(index).hitWindowNanos(true));
        return window;
    }

    void spawned(EntityArrow arrow) {
        // Ordinary arrows also compete for the same hurt packet.
        shots.put(arrow.getEntityId(), new Shot(arrow));
    }

    void collided(EntityArrow arrow, Entity target) {
        Shot shot = shots.get(arrow.getEntityId());
        if (shot == null)
            return;
        capture(shot);
        shot.target = target;
        shot.hitAt = System.nanoTime();
        shot.hitBatch = batch;
        long window = window();
        for (int index = 0, size = hits.size(); index < size; index++) {
            Hit hit = hits.get(index);
            if (hit.player == target
                    && (within(shot.hitAt, batch, hit, window) || hit.batch == batch))
                candidate(shot, hit, shot.hitAt, batch);
        }
    }

    void updated(EntityArrow arrow) {
        Shot shot = shots.get(arrow.getEntityId());
        // A dead arrow can still move past its hit during the rest of the tick.
        if (shot == null || arrow.isDead && shot.hitAt != 0)
            return;
        record(shot, new Vec3(arrow.lastTickPosX, arrow.lastTickPosY, arrow.lastTickPosZ),
                new Vec3(arrow.posX, arrow.posY, arrow.posZ));
    }

    void hurt(EntityPlayer player) {
        if (shots.isEmpty())
            return;
        Hit hit = new Hit(player, System.nanoTime(), player == minecraft.thePlayer, batch);
        for (int index = 0, size = listeners.size(); index < size; index++) {
            ArrowListener listener = listeners.get(index);
            if (listener.hitWindowNanos(true) >= 0)
                hit.pending.add(listener);
        }
        long window = window();
        for (Shot shot : shots.values()) {
            if (shot.endedAt == 0 && !shot.arrow.isDead
                    && !((ArrowAccess) shot.arrow).pit12$isInGround())
                capture(shot);
            if (shot.endedAt != 0 && shot.endedBatch != hit.batch
                    && !within(shot.endedAt, shot.endedBatch, hit, window))
                continue;
            if (shot.target == player && (within(shot.hitAt, shot.hitBatch, hit, window)
                    || hit.batch == shot.hitBatch))
                candidate(shot, hit, shot.hitAt, shot.hitBatch);
            for (Segment segment : shot.path) {
                if (intersects(segment, hit, player == shot.arrow.shootingEntity, window))
                    candidate(shot, hit, segment.at, segment.batch);
            }
        }
        hits.add(hit);
    }

    void removed(EntityArrow arrow, boolean serverRemoved) {
        Shot shot = shots.get(arrow.getEntityId());
        if (shot == null)
            return;
        boolean inGround = ((ArrowAccess) arrow).pit12$isInGround();
        if (!serverRemoved && !arrow.isDead && !inGround) {
            shots.remove(arrow.getEntityId());
            finished(arrow.getEntityId());
            return;
        }
        if (shot.endedAt != 0)
            return;
        // Keep local impacts until the server's hurt and removal packets arrive.
        if (shot.hitAt == 0 || !arrow.isDead)
            capture(shot);
        shot.endedAt = System.nanoTime();
        shot.endedBatch = batch;
    }

    void serverRemoved(int entityId) {
        Shot shot = shots.get(entityId);
        if (shot != null) {
            shot.removedAt = System.nanoTime();
            shot.removedBatch = batch;
        }
    }

    void metadata(int entityId, List<WatchableObject> metadata) {
        Shot shot = shots.get(entityId);
        if (shot == null)
            return;
        for (WatchableObject value : metadata) {
            if (value.getDataValueId() != 16 || !(value.getObject() instanceof Byte))
                continue;
            // Local collisions can clear the entity's copy of the server flag.
            shot.critical = ((Byte) value.getObject() & 1) != 0;
            return;
        }
    }

    private void capture(Shot shot) {
        EntityArrow arrow = shot.arrow;
        Vec3 from = arrow.ticksExisted == 0 ? new Vec3(arrow.posX, arrow.posY, arrow.posZ)
                : new Vec3(arrow.lastTickPosX, arrow.lastTickPosY, arrow.lastTickPosZ);
        Vec3 to = new Vec3(arrow.posX + arrow.motionX, arrow.posY + arrow.motionY,
                arrow.posZ + arrow.motionZ);
        MovingObjectPosition block = arrow.worldObj.rayTraceBlocks(from, to, false, true, false);
        if (block != null)
            to = block.hitVec;
        record(shot, from, to);
    }

    private void record(Shot shot, Vec3 from, Vec3 to) {
        long now = System.nanoTime();
        long window = window();
        while (!shot.path.isEmpty() && shot.path.peekFirst().batch != batch
                && now - shot.path.peekFirst().at > window)
            shot.path.removeFirst();
        // Damage packets can arrive after the arrow has already passed the target.
        Segment segment = new Segment(from, to, now, batch, shot.arrow.ticksExisted >= 5);
        shot.path.addLast(segment);
        for (int index = 0, size = hits.size(); index < size; index++) {
            Hit hit = hits.get(index);
            if (intersects(segment, hit, hit.player == shot.arrow.shootingEntity, window))
                candidate(shot, hit, now, batch);
        }
    }

    private void candidate(Shot shot, Hit hit, long at, long atBatch) {
        long age = Math.abs(hit.at - at);
        Evidence evidence = hit.candidates.get(shot);
        if (evidence == null) {
            hit.candidates.put(shot, new Evidence(age, hit.batch == atBatch));
        } else {
            evidence.age = Math.min(evidence.age, age);
            evidence.sameBatch |= hit.batch == atBatch;
        }
    }

    private boolean intersects(Segment segment, Hit hit, boolean self, long window) {
        return (!self || segment.canHitShooter)
                && (within(segment.at, segment.batch, hit, window) || hit.batch == segment.batch)
                && (intersects(segment, hit.serverBox)
                        || hit.displayBox != hit.serverBox && intersects(segment, hit.displayBox));
    }

    private boolean intersects(Segment segment, AxisAlignedBB box) {
        Vec3 from = segment.from;
        Vec3 to = segment.to;
        if (Math.max(from.xCoord, to.xCoord) < box.minX
                || Math.min(from.xCoord, to.xCoord) > box.maxX
                || Math.max(from.yCoord, to.yCoord) < box.minY
                || Math.min(from.yCoord, to.yCoord) > box.maxY
                || Math.max(from.zCoord, to.zCoord) < box.minZ
                || Math.min(from.zCoord, to.zCoord) > box.maxZ)
            return false;
        return box.isVecInside(from) || box.calculateIntercept(from, to) != null;
    }

    private boolean within(long at, long atBatch, Hit hit, long window) {
        return Math.abs(hit.at - at) <= window || window == 0 && hit.batch == atBatch;
    }

    @HotPath
    void tick() {
        if (shots.isEmpty() && hits.isEmpty()) {
            batch++;
            return;
        }
        long now = System.nanoTime();
        int remaining = 0;
        for (int index = 0, size = hits.size(); index < size; index++) {
            Hit hit = hits.get(index);
            if (hit.player.worldObj == minecraft.theWorld)
                hits.set(remaining++, hit);
        }
        for (int index = hits.size() - 1; index >= remaining; index--)
            hits.remove(index);
        List<Delivery> deliveries = null;
        Map<ArrowListener, Map<Shot, Hit>> ownersByListener = null;
        // Resolve the whole batch before any listener consumes its candidates.
        for (int hitIndex = 0, hitCount = hits.size(); hitIndex < hitCount; hitIndex++) {
            Hit hit = hits.get(hitIndex);
            for (int listenerIndex = 0, listenerCount =
                    hit.pending.size(); listenerIndex < listenerCount; listenerIndex++) {
                ArrowListener listener = hit.pending.get(listenerIndex);
                long window = listener.hitWindowNanos(true);
                if (now - hit.at < window)
                    continue;
                List<Shot> candidates = new ArrayList<>();
                for (Shot shot : hit.candidates.keySet()) {
                    if (matches(shot, hit, listener))
                        candidates.add(shot);
                }
                boolean ambiguous = false;
                if (hits.size() > 1 && !candidates.isEmpty()) {
                    if (ownersByListener == null)
                        ownersByListener = new HashMap<>();
                    Map<Shot, Hit> owners = ownersByListener.get(listener);
                    if (owners == null) {
                        owners = matchOwners(listener);
                        ownersByListener.put(listener, owners);
                    }
                    for (int shotIndex = 0,
                            shotCount = candidates.size(); shotIndex < shotCount; shotIndex++) {
                        Shot shot = candidates.get(shotIndex);
                        if (owners.get(shot) != hit) {
                            ambiguous = true;
                            break;
                        }
                    }
                }
                if (deliveries == null)
                    deliveries = new ArrayList<>();
                deliveries.add(new Delivery(hit, listener, candidates, ambiguous));
            }
        }
        for (int index = 0,
                size = deliveries == null ? 0 : deliveries.size(); index < size; index++) {
            Delivery delivery = deliveries.get(index);
            delivery.hit.pending.remove(delivery.listener);
            for (int shotIndex = 0,
                    shotCount = delivery.candidates.size(); shotIndex < shotCount; shotIndex++)
                delivery.candidates.get(shotIndex).usedBy.add(delivery.listener);
            if (delivery.ambiguous || delivery.candidates.isEmpty())
                continue;
            List<ArrowHit> candidates = new ArrayList<>(delivery.candidates.size());
            for (int shotIndex = 0,
                    shotCount = delivery.candidates.size(); shotIndex < shotCount; shotIndex++) {
                Shot shot = delivery.candidates.get(shotIndex);
                candidates.add(new ArrowHit(shot.arrow, shot.critical));
            }
            delivery.listener.onArrowHit(delivery.hit.player, candidates, delivery.hit.at);
        }
        remaining = 0;
        for (int index = 0, size = hits.size(); index < size; index++) {
            Hit hit = hits.get(index);
            if (!hit.pending.isEmpty())
                hits.set(remaining++, hit);
        }
        for (int index = hits.size() - 1; index >= remaining; index--)
            hits.remove(index);
        long window = window();
        Iterator<Shot> iterator = shots.values().iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            if (shot.endedAt != 0 && shot.endedBatch != batch && now - shot.endedAt > window * 2) {
                iterator.remove();
                finished(shot.arrow.getEntityId());
            }
        }
        batch++;
    }

    private Map<Shot, Hit> matchOwners(ArrowListener listener) {
        // Null marks arrows shared by multiple hits, including hits still waiting.
        Map<Shot, Hit> owners = new HashMap<>();
        for (int index = 0, size = hits.size(); index < size; index++) {
            Hit hit = hits.get(index);
            for (Shot shot : hit.candidates.keySet()) {
                if (!matches(shot, hit, listener))
                    continue;
                if (owners.containsKey(shot))
                    owners.put(shot, null);
                else
                    owners.put(shot, hit);
            }
        }
        return owners;
    }

    private boolean matches(Shot shot, Hit hit, ArrowListener listener) {
        long window = listener.hitWindowNanos(shot.arrow.shootingEntity == minecraft.thePlayer);
        Evidence evidence = hit.candidates.get(shot);
        return evidence != null && (evidence.age <= window || window == 0 && evidence.sameBatch)
                && !shot.usedBy.contains(listener) && shot.removedAt != 0
                && within(shot.removedAt, shot.removedBatch, hit, window);
    }

    private void finished(int entityId) {
        for (int index = 0, size = listeners.size(); index < size; index++) {
            ArrowListener listener = listeners.get(index);
            if (listener.hitWindowNanos(true) >= 0)
                listener.onArrowFinished(entityId);
        }
    }

    void removeListener(ArrowListener listener) {
        for (int index = 0, size = hits.size(); index < size; index++)
            hits.get(index).pending.remove(listener);
    }

    void clear() {
        shots.clear();
        hits.clear();
    }

    private static final class Shot {
        final EntityArrow arrow;
        final Deque<Segment> path = new ArrayDeque<>();
        final Set<ArrowListener> usedBy = new HashSet<>();
        boolean critical;
        Entity target;
        long hitAt;
        long hitBatch;
        long endedAt;
        long endedBatch;
        long removedAt;
        long removedBatch;

        Shot(EntityArrow arrow) {
            this.arrow = arrow;
            critical = arrow.getIsCritical();
        }
    }
    private static final class Segment {
        final Vec3 from;
        final Vec3 to;
        final long at;
        final long batch;
        final boolean canHitShooter;

        Segment(Vec3 from, Vec3 to, long at, long batch, boolean canHitShooter) {
            this.from = from;
            this.to = to;
            this.at = at;
            this.batch = batch;
            this.canHitShooter = canHitShooter;
        }
    }
    private static final class Hit {
        final EntityPlayer player;
        final long at;
        final long batch;
        final AxisAlignedBB displayBox;
        final AxisAlignedBB serverBox;
        final Map<Shot, Evidence> candidates = new HashMap<>();
        final List<ArrowListener> pending = new ArrayList<>();

        Hit(EntityPlayer player, long at, boolean local, long batch) {
            this.player = player;
            this.at = at;
            this.batch = batch;
            displayBox = player.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
            // Remote player positions interpolate toward the server coordinates.
            serverBox = local ? displayBox
                    : displayBox.offset(player.serverPosX / 32.0 - player.posX,
                            player.serverPosY / 32.0 - player.posY,
                            player.serverPosZ / 32.0 - player.posZ);
        }
    }
    private static final class Evidence {
        long age;
        boolean sameBatch;

        Evidence(long age, boolean sameBatch) {
            this.age = age;
            this.sameBatch = sameBatch;
        }
    }
    private static final class Delivery {
        final Hit hit;
        final ArrowListener listener;
        final List<Shot> candidates;
        final boolean ambiguous;

        Delivery(Hit hit, ArrowListener listener, List<Shot> candidates, boolean ambiguous) {
            this.hit = hit;
            this.listener = listener;
            this.candidates = candidates;
            this.ambiguous = ambiguous;
        }
    }
}
