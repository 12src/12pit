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
package pit12.platform.mixin.feature.render.timers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.timers.JumpBoostPacketBinding;
import pit12.feature.render.timers.JumpBoostTracker;
import pit12.feature.render.timers.VenomPacketBinding;
import pit12.feature.render.timers.VenomTracker;

@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin
        implements VenomPacketBinding, JumpBoostPacketBinding {
    @Shadow
    private Minecraft gameController;
    @Unique
    private VenomTracker pit12$venomTracker;
    @Unique
    private JumpBoostTracker pit12$jumpBoostTracker;

    @Override
    public void bindVenomTracker(VenomTracker tracker) {
        pit12$venomTracker = tracker;
    }

    @Override
    public void bindJumpBoostTracker(JumpBoostTracker tracker) {
        pit12$jumpBoostTracker = tracker;
    }

    @Inject(method = "handleSoundEffect", at = @At("TAIL"))
    private void pit12$afterJumpBoostSound(S29PacketSoundEffect packet, CallbackInfo ci) {
        if (pit12$jumpBoostTracker != null && "random.drink".equals(packet.getSoundName())
                && (Object) this == gameController.getNetHandler())
            pit12$jumpBoostTracker.sound(packet.getX(), packet.getY(), packet.getZ());
    }

    @Inject(method = "handleEntityMovement", at = @At("TAIL"))
    private void pit12$afterPlayerMovement(S14PacketEntity packet, CallbackInfo ci) {
        boolean venom = pit12$venomTracker != null && pit12$venomTracker.trackingHits()
                && packet.func_149060_h(); // has rotation
        boolean jumping = pit12$jumpBoostTracker != null && pit12$jumpBoostTracker.checkingJumps();
        if ((!venom && !jumping) || (Object) this != gameController.getNetHandler()
                || gameController.theWorld == null)
            return;
        Entity entity = packet.getEntity(gameController.theWorld);
        if (entity instanceof EntityPlayer) {
            if (venom)
                pit12$venomTracker.rotation((EntityPlayer) entity, packet.func_149066_f(), // yaw
                        packet.func_149063_g()); // pitch
            if (jumping)
                pit12$jumpBoostTracker.observeJump((EntityPlayer) entity, false);
        }
    }

    @Inject(method = "handleEntityTeleport", at = @At("TAIL"))
    private void pit12$afterPlayerTeleport(S18PacketEntityTeleport packet, CallbackInfo ci) {
        boolean venom = pit12$venomTracker != null && pit12$venomTracker.trackingHits();
        boolean jumping = pit12$jumpBoostTracker != null && pit12$jumpBoostTracker.checkingJumps();
        if ((!venom && !jumping) || (Object) this != gameController.getNetHandler()
                || gameController.theWorld == null)
            return;
        Entity entity = gameController.theWorld.getEntityByID(packet.getEntityId());
        // Absolute packets also carry normal takeoff and landing updates.
        if (entity instanceof EntityPlayer) {
            if (venom)
                pit12$venomTracker.rotation((EntityPlayer) entity, packet.getYaw(),
                        packet.getPitch());
            if (jumping)
                pit12$jumpBoostTracker.observeJump((EntityPlayer) entity, false);
        }
    }

    @Inject(method = "handleEntityVelocity", at = @At("TAIL"))
    private void pit12$afterJumpBoostVelocity(S12PacketEntityVelocity packet, CallbackInfo ci) {
        if (pit12$jumpBoostTracker == null || !pit12$jumpBoostTracker.checkingJumps()
                || (Object) this != gameController.getNetHandler()
                || gameController.theWorld == null)
            return;
        Entity entity = gameController.theWorld.getEntityByID(packet.getEntityID());
        if (entity instanceof EntityPlayer)
            pit12$jumpBoostTracker.observeJump((EntityPlayer) entity, true);
    }

    @Inject(method = {"handlePlayerPosLook", "handleExplosion"}, at = @At("TAIL"))
    private void pit12$afterJumpBoostLocalCorrection(CallbackInfo ci) {
        if (pit12$jumpBoostTracker != null && pit12$jumpBoostTracker.checkingJumps()
                && (Object) this == gameController.getNetHandler()
                && gameController.thePlayer != null)
            pit12$jumpBoostTracker.observeJump(gameController.thePlayer, true);
    }

    @Inject(method = "handleAnimation", at = @At("TAIL"))
    private void pit12$afterVenomAnimation(S0BPacketAnimation packet, CallbackInfo ci) {
        if (pit12$venomTracker == null || !pit12$venomTracker.trackingHits()
                || packet.getAnimationType() != 0 || (Object) this != gameController.getNetHandler()
                || gameController.theWorld == null)
            return;
        Entity entity = gameController.theWorld.getEntityByID(packet.getEntityID());
        if (entity instanceof EntityPlayer)
            pit12$venomTracker.swing((EntityPlayer) entity);
    }

    @Inject(method = "handleEntityStatus", at = @At("TAIL"))
    private void pit12$afterVenomHurt(S19PacketEntityStatus packet, CallbackInfo ci) {
        if (pit12$venomTracker == null || !pit12$venomTracker.trackingHits()
                || packet.getOpCode() != 2 || (Object) this != gameController.getNetHandler()
                || gameController.theWorld == null)
            return;
        Entity entity = packet.getEntity(gameController.theWorld);
        if (entity instanceof EntityPlayer)
            pit12$venomTracker.hurt((EntityPlayer) entity);
    }
}
