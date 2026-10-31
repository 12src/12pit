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
package pit12.feature.render.freecam.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin {
    @Shadow
    public MovementInput movementInput;
    @Unique
    private final MovementInput pit12$frozenInput = new MovementInput();
    @Unique
    private MovementInput pit12$realInput;

    @Inject(method = "isCurrentViewEntity", at = @At("HEAD"), cancellable = true)
    private void pit12$keepSendingPlayerMovement(CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if ((Object) this != minecraft.thePlayer) {
            return;
        }
        FreeCameraFeature feature = ((FreeCameraBinding) minecraft).pit12$freeCamera();
        if (feature != null && feature.isActive()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void pit12$freezeMovement(CallbackInfo callback) {
        FreeCameraFeature feature =
                ((FreeCameraBinding) Minecraft.getMinecraft()).pit12$freeCamera();
        if (feature != null && feature.isActive()) {
            EntityPlayerSP player = (EntityPlayerSP) (Object) this;
            movementInput.moveForward = 0;
            movementInput.moveStrafe = 0;
            movementInput.jump = false;
            movementInput.sneak = false;
            player.moveForward = 0;
            player.moveStrafing = 0;
            player.setJumping(false);
            pit12$realInput = movementInput;
            movementInput = pit12$frozenInput;
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void pit12$restoreMovement(CallbackInfo callback) {
        if (pit12$realInput != null) {
            movementInput = pit12$realInput;
            pit12$realInput = null;
        }
    }
}
