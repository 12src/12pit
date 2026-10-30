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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import pit12.runtime.arrow.ArrowAccess;

final class TelebowPath {
    final EntityArrow arrow;
    final EntityPlayer shooter;
    final String displayName;
    final TelebowTrajectory trajectory = new TelebowTrajectory();
    int color;
    boolean dirty;
    long endedAt;
    long revision;
    private final int age;
    private int progress;
    private boolean corrected;
    private boolean velocityCorrected;
    private int correctionStep;
    private double correctionX;
    private double correctionY;
    private double correctionZ;
    private double correctionMotionX;
    private double correctionMotionY;
    private double correctionMotionZ;

    TelebowPath(EntityArrow arrow, EntityPlayer shooter, int color) {
        this.arrow = arrow;
        this.shooter = shooter;
        this.color = color;
        displayName = shooter.getDisplayName().getFormattedText();
        age = arrow.ticksExisted;
        trajectory.trace(arrow.worldObj, shooter, arrow, arrow.posX, arrow.posY, arrow.posZ,
                arrow.motionX, arrow.motionY, arrow.motionZ, age, 0);
    }

    void updateProgress() {
        progress = (int) locate(arrow.posX, arrow.posY, arrow.posZ);
        if (trajectory.hasImpact)
            progress = Math.min(progress, trajectory.count - 2);
    }

    private double locate(double observedX, double observedY, double observedZ) {
        double[] points = trajectory.points;
        double closest = Double.MAX_VALUE;
        double observedProgress = 0;
        for (int index = 0; index + 1 < trajectory.count; index++) {
            int offset = index * 3;
            double x = points[offset];
            double y = points[offset + 1];
            double z = points[offset + 2];
            double dx = trajectory.motion[offset];
            double dy = trajectory.motion[offset + 1];
            double dz = trajectory.motion[offset + 2];
            double length = dx * dx + dy * dy + dz * dz;
            if (length == 0)
                continue;
            double projection =
                    ((observedX - x) * dx + (observedY - y) * dy + (observedZ - z) * dz) / length;
            double fraction = Math.max(0, Math.min(1, projection));
            double errorX = observedX - x - dx * fraction;
            double errorY = observedY - y - dy * fraction;
            double errorZ = observedZ - z - dz * fraction;
            double distance = errorX * errorX + errorY * errorY + errorZ * errorZ;
            if (distance < closest) {
                closest = distance;
                observedProgress = index + fraction;
            }
        }
        return observedProgress;
    }

    void correct(boolean velocityChanged) {
        if (!corrected || !velocityChanged) {
            correctionStep = (int) Math.round(locate(arrow.posX, arrow.posY, arrow.posZ));
            correctionX = arrow.posX;
            correctionY = arrow.posY;
            correctionZ = arrow.posZ;
        }
        if (velocityChanged) {
            correctionMotionX = arrow.motionX;
            correctionMotionY = arrow.motionY;
            correctionMotionZ = arrow.motionZ;
            velocityCorrected = true;
        } else if (!velocityCorrected) {
            // Position packets do not update velocity to the same flight tick.
            int offset = correctionStep * 3;
            correctionMotionX = trajectory.motion[offset];
            correctionMotionY = trajectory.motion[offset + 1];
            correctionMotionZ = trajectory.motion[offset + 2];
        }
        corrected = true;
        dirty = true;
    }

    void finish(long now) {
        if (endedAt != 0)
            return;
        if (dirty)
            predict();
        if (((ArrowAccess) arrow).pit12$isInGround()) {
            int end = Math.min((int) locate(arrow.posX, arrow.posY, arrow.posZ) + 1,
                    trajectory.count - 1);
            int offset = end * 3;
            trajectory.points[offset] = arrow.posX;
            trajectory.points[offset + 1] = arrow.posY;
            trajectory.points[offset + 2] = arrow.posZ;
            trajectory.count = end + 1;
            trajectory.hasImpact = true;
        }
        endedAt = now;
        dirty = false;
        revision++;
    }

    float opacity(long now, long hold, long fade) {
        if (endedAt == 0)
            return 1;
        long elapsed = now - endedAt - hold;
        return elapsed <= 0 ? 1 : Math.max(0, 1 - (float) elapsed / fade);
    }

    boolean affectedBy(BlockPos position) {
        double[] points = trajectory.points;
        AxisAlignedBB bounds = trajectory.bounds;
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        if (x + 1 < bounds.minX || x > bounds.maxX || y + 1 < bounds.minY || y > bounds.maxY
                || z + 1 < bounds.minZ || z > bounds.maxZ)
            return false;
        if (trajectory.count < 2)
            return true;
        for (int index = Math.min(progress, trajectory.count - 2); index
                + 1 < trajectory.count; index++) {
            int offset = index * 3;
            if (x + 1 >= Math.min(points[offset], points[offset + 3]) - 1
                    && x <= Math.max(points[offset], points[offset + 3]) + 1
                    && y + 1 >= Math.min(points[offset + 1], points[offset + 4]) - 1
                    && y <= Math.max(points[offset + 1], points[offset + 4]) + 1
                    && z + 1 >= Math.min(points[offset + 2], points[offset + 5]) - 1
                    && z <= Math.max(points[offset + 2], points[offset + 5]) + 1)
                return true;
        }
        return false;
    }

    void predict() {
        int start = corrected ? correctionStep : progress;
        int offset = start * 3;
        double x = corrected ? correctionX : trajectory.points[offset];
        double y = corrected ? correctionY : trajectory.points[offset + 1];
        double z = corrected ? correctionZ : trajectory.points[offset + 2];
        double motionX = corrected ? correctionMotionX : trajectory.motion[offset];
        double motionY = corrected ? correctionMotionY : trajectory.motion[offset + 1];
        double motionZ = corrected ? correctionMotionZ : trajectory.motion[offset + 2];
        if (corrected) {
            double dx = x - trajectory.points[offset];
            double dy = y - trajectory.points[offset + 1];
            double dz = z - trajectory.points[offset + 2];
            // The impact point can fall between flight ticks.
            if (trajectory.hasImpact && start == trajectory.count - 1) {
                dx = x - trajectory.points[offset - 3] - trajectory.motion[offset - 3];
                dy = y - trajectory.points[offset - 2] - trajectory.motion[offset - 2];
                dz = z - trajectory.points[offset - 1] - trajectory.motion[offset - 1];
            }
            // Apply position sync to the whole curve, including the trail.
            for (int index = 0; index < offset; index += 3) {
                trajectory.points[index] += dx;
                trajectory.points[index + 1] += dy;
                trajectory.points[index + 2] += dz;
            }
        }
        trajectory.trace(arrow.worldObj, shooter, arrow, x, y, z, motionX, motionY, motionZ, age,
                start);
        progress = start;
        corrected = false;
        velocityCorrected = false;
        dirty = false;
        revision++;
    }
}
