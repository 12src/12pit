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
package pit12.feature.render.freecam;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * The detached camera. It is never added to the world and never ticks on its own: the feature moves it and
 * {@code Minecraft.setRenderViewEntity} makes the renderer use it as the viewpoint.
 */
public final class FreeCameraEntity extends Entity {
    public FreeCameraEntity(World world) {
        super(world);
        noClip = true;
        setSize(0.25F, 0.25F);
        setInvisible(true);
    }

    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompound) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {}

    @Override
    public float getEyeHeight() {
        // The position already is the eye position, so the renderer must not add an offset.
        return 0.0F;
    }

    public void placeAt(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        rotationYaw = yaw;
        rotationPitch = pitch;
        prevRotationYaw = yaw;
        prevRotationPitch = pitch;
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        lastTickPosX = x;
        lastTickPosY = y;
        lastTickPosZ = z;
    }

    /**
     * Mirrors what {@code Entity.onEntityUpdate()} does for a ticking entity: remember where we were before this tick's
     * movement so the renderer can interpolate. Without it the renderer keeps interpolating from the position before the
     * last movement step, which makes the view oscillate once per tick while the camera is standing still.
     */
    public void syncTickPosition() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        lastTickPosX = posX;
        lastTickPosY = posY;
        lastTickPosZ = posZ;
    }

    public void moveTo(double x, double y, double z) {
        setPosition(x, y, z);
    }

    public void rotate(float yawChange, float pitchChange) {
        setAngles(yawChange, pitchChange);
    }
}
