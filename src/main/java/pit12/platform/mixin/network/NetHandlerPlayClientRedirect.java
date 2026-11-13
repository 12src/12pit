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
package pit12.platform.mixin.network;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import pit12.runtime.arrow.ArrowPacketBinding;
import pit12.runtime.arrow.ArrowPacketObserver;

@Mixin(value = NetHandlerPlayClient.class, priority = -1)
public abstract class NetHandlerPlayClientRedirect {
    // Relative packet coordinates have already been resolved here.
    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void pit12$observeLocalTeleport(S08PacketPlayerPosLook packet, CallbackInfo ci,
            EntityPlayer player, double x, double y, double z) {
        ArrowPacketObserver observer = ((ArrowPacketBinding) this).pit12$arrowObserver();
        if (observer != null) {
            observer.onShooterTeleport(player.getEntityId(), x, y, z);
        }
    }

    @Redirect(method = "handleEntityVelocity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocity(DDD)V"))
    private void pit12$observeArrowVelocity(Entity entity, double x, double y, double z) {
        ArrowPacketObserver observer = ((ArrowPacketBinding) this).pit12$arrowObserver();
        if (observer == null || !(entity instanceof EntityArrow)) {
            entity.setVelocity(x, y, z);
            return;
        }
        double previousX = entity.motionX;
        double previousY = entity.motionY;
        double previousZ = entity.motionZ;
        entity.setVelocity(x, y, z);
        if (entity.motionX != previousX || entity.motionY != previousY
                || entity.motionZ != previousZ) {
            observer.onArrowCorrected(entity.getEntityId(), true);
        }
    }

    @Redirect(method = {"handleEntityTeleport", "handleEntityMovement"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;setPositionAndRotation2(DDDFFIZ)V"))
    private void pit12$observeArrowPosition(Entity entity, double x, double y, double z, float yaw,
            float pitch, int increments, boolean teleport) {
        ArrowPacketObserver observer = ((ArrowPacketBinding) this).pit12$arrowObserver();
        if (observer == null || !(entity instanceof EntityArrow)) {
            entity.setPositionAndRotation2(x, y, z, yaw, pitch, increments, teleport);
            return;
        }
        double previousX = entity.posX;
        double previousY = entity.posY;
        double previousZ = entity.posZ;
        entity.setPositionAndRotation2(x, y, z, yaw, pitch, increments, teleport);
        if (entity.posX != previousX || entity.posY != previousY || entity.posZ != previousZ) {
            observer.onArrowCorrected(entity.getEntityId(), false);
        }
    }
}
