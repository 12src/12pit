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

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

final class FreeCameraEntity extends EntityPlayerSP {
    FreeCameraEntity(Minecraft minecraft, EntityPlayerSP player) {
        super(minecraft, player.worldObj, player.sendQueue, player.getStatFileWriter());
        noClip = true;
        setLocationAndAngles(player.posX, player.posY, player.posZ, player.rotationYaw,
                player.rotationPitch);
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        rotationYawHead = renderYawOffset = rotationYaw;
        updateChunkPosition();
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    // A normal player tick also runs physics and sends packets.
    @Override
    public void onUpdate() {
        prevPosX = lastTickPosX = posX;
        prevPosY = lastTickPosY = posY;
        prevPosZ = lastTickPosZ = posZ;
    }

    @Override
    public void moveEntity(double x, double y, double z) {
        super.moveEntity(x, y, z);
        updateChunkPosition();
    }

    private void updateChunkPosition() {
        chunkCoordX = MathHelper.floor_double(posX / 16.0);
        chunkCoordY = MathHelper.floor_double(posY / 16.0);
        chunkCoordZ = MathHelper.floor_double(posZ / 16.0);
    }
}
