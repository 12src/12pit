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
package pit12.feature.telebow;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;

final class TelebowAim {
    private static final double[] DISTANCE = new double[TelebowTrajectory.MAX_STEPS + 1];
    private static final double[] DROP = new double[TelebowTrajectory.MAX_STEPS + 1];
    static {
        double speed = 1;
        double fall = 0;
        for (int step = 1; step < DISTANCE.length; step++) {
            DISTANCE[step] = DISTANCE[step - 1] + speed;
            DROP[step] = DROP[step - 1] + fall;
            speed *= TelebowTrajectory.AIR_DRAG;
            fall = fall * TelebowTrajectory.AIR_DRAG + TelebowTrajectory.GRAVITY;
        }
    }
    private final Minecraft minecraft;
    private final TelebowConfig config;
    private final ToIntFunction<EntityPlayer> colorOf;
    final Map<TelebowPath, Target> targets = new IdentityHashMap<>();
    private final TelebowTrajectory trajectory = new TelebowTrajectory();
    private int tick;

    TelebowAim(Minecraft minecraft, TelebowConfig config, ToIntFunction<EntityPlayer> colorOf) {
        this.minecraft = minecraft;
        this.config = config;
        this.colorOf = colorOf;
    }

    void update(Collection<TelebowPath> paths) {
        EntityPlayer shooter = minecraft.thePlayer;
        if (shooter == null || minecraft.theWorld == null || !shooter.isEntityAlive()
                || shooter.isPlayerSleeping()) {
            clear();
            return;
        }
        if (config.aimHoldingOnly.get()) {
            ItemStack bow = shooter.getHeldItem();
            if (bow == null || !(bow.getItem() instanceof ItemBow)
                    || PitEnchantmentReader.levelOf(bow, PitEnchantment.Telebow) == 0) {
                clear();
                return;
            }
        }
        tick++;
        for (TelebowPath path : paths) {
            int color = colorOf.applyAsInt(path.shooter);
            if ((color >>> 24) == 0)
                continue;
            Target target = targets.computeIfAbsent(path, Target::new);
            target.seenAt = tick;
            target.color = color;
            if (target.bestSample >= 0 && !solve(shooter, target, target.bestSample)) {
                target.bestSample = -1;
            }
            if (target.bestSample < 0) {
                int sample = target.nextPoint * 2;
                if (!solve(shooter, target, sample))
                    solve(shooter, target, sample + 1);
                target.nextPoint = (target.nextPoint + 1) % 11;
            }
        }
        targets.values().removeIf(target -> target.seenAt != tick);
    }

    private boolean solve(EntityPlayer shooter, Target target, int sample) {
        if (!position(shooter, target, sample, 1))
            return false;
        // Bow spread is random, so this checks the center trajectory.
        trajectory.trace(minecraft.theWorld, shooter, null, target.launchX, target.launchY,
                target.launchZ, target.directionX * 3, target.directionY * 3, target.directionZ * 3,
                0, 0);
        if (!trajectory.hasImpact)
            return false;
        int offset = (trajectory.count - 1) * 3;
        TelebowTrajectory prediction = target.path.trajectory;
        int landing = (prediction.count - 1) * 3;
        double dx = trajectory.points[offset] - prediction.points[landing];
        double dy = trajectory.points[offset + 1] - prediction.points[landing + 1];
        double dz = trajectory.points[offset + 2] - prediction.points[landing + 2];
        double radius = config.aimTolerance.get();
        if (dx * dx + dy * dy + dz * dz > radius * radius)
            return false;
        target.bestSample = sample;
        return true;
    }

    void interpolate(float partialTicks) {
        EntityPlayer shooter = minecraft.thePlayer;
        if (shooter == null)
            return;
        for (Target target : targets.values()) {
            target.renderable = target.bestSample >= 0
                    && position(shooter, target, target.bestSample, partialTicks);
        }
    }

    private boolean position(EntityPlayer shooter, Target target, int sample, float partialTicks) {
        TelebowTrajectory prediction = target.path.trajectory;
        if (!prediction.hasImpact)
            return false;
        double sourceX =
                shooter.lastTickPosX + (shooter.posX - shooter.lastTickPosX) * partialTicks;
        double sourceY =
                shooter.lastTickPosY + (shooter.posY - shooter.lastTickPosY) * partialTicks;
        double sourceZ =
                shooter.lastTickPosZ + (shooter.posZ - shooter.lastTickPosZ) * partialTicks;
        int landing = (prediction.count - 1) * 3;
        double x = prediction.points[landing];
        double y = prediction.points[landing + 1];
        double z = prediction.points[landing + 2];
        double radius = config.aimTolerance.get();
        int point = sample / 2;
        if (point > 0 && point <= 8) {
            double angle = (point - 1) * Math.PI / 4;
            x += Math.cos(angle) * radius * 0.75;
            z += Math.sin(angle) * radius * 0.75;
        } else if (point > 8) {
            y += (point == 9 ? -0.75 : 0.75) * radius;
        }
        double dx = x - sourceX;
        double dz = z - sourceZ;
        double distanceSquared = dx * dx + dz * dz;
        double sideways = 0.16F;
        double horizontal = Math.sqrt(Math.max(0, distanceSquared - sideways * sideways));
        double cos;
        double sin;
        if (horizontal == 0) {
            double yaw = Math.toRadians(shooter.rotationYaw);
            cos = -Math.sin(yaw);
            sin = Math.cos(yaw);
        } else {
            // The bow spawns arrows 0.16 blocks to the side of the player's eye.
            cos = (dx * horizontal + dz * sideways) / distanceSquared;
            sin = (dz * horizontal - dx * sideways) / distanceSquared;
        }
        // Solve from the crouched release height, even before the player starts sneaking.
        float releaseEyeHeight = shooter.getEyeHeight() - (shooter.isSneaking() ? 0 : 0.08F);
        target.launchX = sourceX - sin * sideways;
        target.launchY = sourceY + releaseEyeHeight - 0.1F;
        target.launchZ = sourceZ + cos * sideways;
        double dy = y - target.launchY;
        double time = flightTime(horizontal * horizontal, dy, sample % 2 != 0);
        if (time < 0)
            return false;
        int step = (int) time;
        double fraction = time - step;
        double drop = DROP[step] + (DROP[step + 1] - DROP[step]) * fraction;
        double vertical = dy + drop;
        double length = Math.sqrt(horizontal * horizontal + vertical * vertical);
        target.directionX = cos * horizontal / length;
        target.directionY = vertical / length;
        target.directionZ = sin * horizontal / length;
        target.x = sourceX + target.directionX * length;
        target.y = sourceY + shooter.getEyeHeight() + target.directionY * length;
        target.z = sourceZ + target.directionZ * length;
        target.distance = length;
        return true;
    }

    private static double flightTime(double horizontalSquared, double dy, boolean high) {
        // Between ticks the arrow follows a straight segment. Interpolate both sums the same way.
        double previous = horizontalSquared + dy * dy;
        for (int step = 1; step < DISTANCE.length; step++) {
            double vertical = dy + DROP[step];
            double error =
                    horizontalSquared + vertical * vertical - 9 * DISTANCE[step] * DISTANCE[step];
            if (high ? previous <= 0 && error > 0 : previous > 0 && error <= 0) {
                double lower = 0;
                double upper = 1;
                for (int iteration = 0; iteration < 16; iteration++) {
                    double middle = (lower + upper) / 2;
                    double distance =
                            DISTANCE[step - 1] + (DISTANCE[step] - DISTANCE[step - 1]) * middle;
                    vertical = dy + DROP[step - 1] + (DROP[step] - DROP[step - 1]) * middle;
                    double next = horizontalSquared + vertical * vertical - 9 * distance * distance;
                    if ((next > 0) == (previous > 0))
                        lower = middle;
                    else
                        upper = middle;
                }
                return step - 1 + (lower + upper) / 2;
            }
            previous = error;
        }
        return -1;
    }

    void clear() {
        if (!targets.isEmpty())
            targets.clear();
    }

    static final class Target {
        final TelebowPath path;
        int color;
        int seenAt;
        int nextPoint;
        int bestSample = -1;
        boolean renderable;
        double launchX;
        double launchY;
        double launchZ;
        double directionX;
        double directionY;
        double directionZ;
        double x;
        double y;
        double z;
        double distance;
        float alpha;
        float scale;
        int nameWidth = -1;
        boolean unicode;

        Target(TelebowPath path) {
            this.path = path;
        }
    }
}
