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
package pit12.feature.combat.enhancedtelebow.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowBinding;
import pit12.feature.combat.enhancedtelebow.EnhancedTelebowFeature;
import pit12.feature.combat.enhancedtelebow.TelebowPlayer;
import pit12.feature.combat.enhancedtelebow.TelebowRelease;

@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin implements TelebowPlayer {
    @Shadow
    protected Minecraft mc;
    @Shadow
    public MovementInput movementInput;
    @Shadow
    private boolean serverSneakState;
    @Shadow
    private float lastReportedYaw;
    @Shadow
    private float lastReportedPitch;

    @Override
    public void pit12$recordTeleportLook(float yaw, float pitch) {
        lastReportedYaw = yaw;
        lastReportedPitch = pitch;
    }

    @Override
    public boolean pit12$sentSneakState() {
        return serverSneakState;
    }

    @Inject(method = "onLivingUpdate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
                    shift = At.Shift.AFTER))
    private void pit12$sneakBeforeTelebowRelease(CallbackInfo ci) {
        EnhancedTelebowFeature feature = ((EnhancedTelebowBinding) mc).pit12$enhancedTelebow();
        TelebowRelease release = feature == null ? null : feature.release();
        if (release != null && !movementInput.sneak
                && release.shouldSneak((EntityPlayerSP) (Object) this)) {
            movementInput.sneak = true;
            movementInput.moveStrafe *= 0.3;
            movementInput.moveForward *= 0.3;
        }
    }
}
