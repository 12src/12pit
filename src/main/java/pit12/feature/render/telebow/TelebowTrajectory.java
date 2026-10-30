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
package pit12.feature.render.telebow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

final class TelebowTrajectory {
    static final int MAX_STEPS = 160;
    static final float AIR_DRAG = 0.99F;
    static final float GRAVITY = 0.05F;
    private static final int BATCH_STEPS = 16;
    private final List<AxisAlignedBB> boxes = new ArrayList<>();
    double[] points = new double[(MAX_STEPS + 1) * 3];
    // Velocities cover whole ticks, even when the impact point falls between ticks.
    double[] motion = new double[(MAX_STEPS + 1) * 3];
    int count = 1;
    boolean hasImpact;
    AxisAlignedBB bounds;

    void trace(World world, EntityPlayer shooter, Entity excluded, double x, double y, double z,
            double motionX, double motionY, double motionZ, int initialAge, int startStep) {
        int capacity = (startStep + MAX_STEPS + 1) * 3;
        if (capacity > points.length) {
            capacity = Math.max(capacity, points.length * 2);
            points = Arrays.copyOf(points, capacity);
            motion = Arrays.copyOf(motion, capacity);
        }
        count = startStep + 1;
        hasImpact = false;
        int initial = startStep * 3;
        points[initial] = x;
        points[initial + 1] = y;
        points[initial + 2] = z;
        motion[initial] = motionX;
        motion[initial + 1] = motionY;
        motion[initial + 2] = motionZ;
        Vec3 from = new Vec3(x, y, z);
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        int chunkX = MathHelper.floor_double(x) >> 4;
        int chunkZ = MathHelper.floor_double(z) >> 4;
        boolean stopped = !world.isBlockLoaded(
                block.set(MathHelper.floor_double(x), 64, MathHelper.floor_double(z)));
        // Short batches keep entity queries narrow and stop simulation soon after an entity hit.
        for (int first = startStep; first < startStep + MAX_STEPS && !stopped; first +=
                BATCH_STEPS) {
            double minX = x, minY = y, minZ = z;
            double maxX = x, maxY = y, maxZ = z;
            for (int step = first; step < Math.min(first + BATCH_STEPS,
                    startStep + MAX_STEPS); step++) {
                Vec3 to = new Vec3(x + motionX, y + motionY, z + motionZ);
                int nextX = MathHelper.floor_double(to.xCoord);
                int nextZ = MathHelper.floor_double(to.zCoord);
                if (to.yCoord < 0 || ((nextX >> 4) != chunkX || (nextZ >> 4) != chunkZ)
                        && !world.isBlockLoaded(block.set(nextX, 64, nextZ))) {
                    stopped = true;
                    break;
                }
                chunkX = nextX >> 4;
                chunkZ = nextZ >> 4;
                MovingObjectPosition hit = world.rayTraceBlocks(from, to, false, true, false);
                if (hit != null) {
                    to = hit.hitVec;
                    hasImpact = true;
                    stopped = true;
                }
                int offset = count++ * 3;
                points[offset] = to.xCoord;
                points[offset + 1] = to.yCoord;
                points[offset + 2] = to.zCoord;
                minX = Math.min(minX, to.xCoord);
                minY = Math.min(minY, to.yCoord);
                minZ = Math.min(minZ, to.zCoord);
                maxX = Math.max(maxX, to.xCoord);
                maxY = Math.max(maxY, to.yCoord);
                maxZ = Math.max(maxZ, to.zCoord);
                float drag = world.isMaterialInBB(
                        new AxisAlignedBB(x - 0.25, y, z - 0.25, x + 0.25, y + 0.5, z + 0.25),
                        Material.water) ? 0.6F : AIR_DRAG;
                x = to.xCoord;
                y = to.yCoord;
                z = to.zCoord;
                from = to;
                motionX *= drag;
                motionY = motionY * drag - GRAVITY;
                motionZ *= drag;
                motion[offset] = motionX;
                motion[offset + 1] = motionY;
                motion[offset + 2] = motionZ;
                if (stopped)
                    break;
            }
            if (count == first + 1)
                break;
            List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(excluded,
                    new AxisAlignedBB(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1));
            boxes.clear();
            int shooterIndex = -1;
            for (Entity entity : entities) {
                if (entity.isDead || !entity.canBeCollidedWith())
                    continue;
                if (entity instanceof EntityPlayer
                        && (((EntityPlayer) entity).capabilities.disableDamage
                                || !shooter.canAttackPlayer((EntityPlayer) entity)))
                    continue;
                if (entity == shooter)
                    shooterIndex = boxes.size();
                boxes.add(entity.getEntityBoundingBox().expand(0.3, 0.3, 0.3));
            }
            if (boxes.isEmpty())
                continue;
            int startOffset = first * 3;
            Vec3 start =
                    new Vec3(points[startOffset], points[startOffset + 1], points[startOffset + 2]);
            for (int segment = first + 1; segment < count; segment++) {
                int offset = segment * 3;
                Vec3 end = new Vec3(points[offset], points[offset + 1], points[offset + 2]);
                double segmentMinX = Math.min(start.xCoord, end.xCoord);
                double segmentMinY = Math.min(start.yCoord, end.yCoord);
                double segmentMinZ = Math.min(start.zCoord, end.zCoord);
                double segmentMaxX = Math.max(start.xCoord, end.xCoord);
                double segmentMaxY = Math.max(start.yCoord, end.yCoord);
                double segmentMaxZ = Math.max(start.zCoord, end.zCoord);
                Vec3 closest = null;
                double distance = Double.MAX_VALUE;
                for (int index = 0; index < boxes.size(); index++) {
                    if (index == shooterIndex && initialAge + segment < 5)
                        continue;
                    AxisAlignedBB box = boxes.get(index);
                    if (segmentMaxX < box.minX || segmentMinX > box.maxX || segmentMaxY < box.minY
                            || segmentMinY > box.maxY || segmentMaxZ < box.minZ
                            || segmentMinZ > box.maxZ)
                        continue;
                    MovingObjectPosition hit = box.calculateIntercept(start, end);
                    if (hit != null) {
                        double next = start.squareDistanceTo(hit.hitVec);
                        if (next < distance) {
                            closest = hit.hitVec;
                            distance = next;
                        }
                    }
                }
                if (closest != null) {
                    points[offset] = closest.xCoord;
                    points[offset + 1] = closest.yCoord;
                    points[offset + 2] = closest.zCoord;
                    count = segment + 1;
                    hasImpact = true;
                    stopped = true;
                    break;
                }
                start = end;
            }
        }
        double minX = points[0], minY = points[1], minZ = points[2];
        double maxX = minX, maxY = minY, maxZ = minZ;
        for (int index = 1; index < count; index++) {
            int offset = index * 3;
            minX = Math.min(minX, points[offset]);
            minY = Math.min(minY, points[offset + 1]);
            minZ = Math.min(minZ, points[offset + 2]);
            maxX = Math.max(maxX, points[offset]);
            maxY = Math.max(maxY, points[offset + 1]);
            maxZ = Math.max(maxZ, points[offset + 2]);
        }
        bounds = new AxisAlignedBB(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
    }
}
