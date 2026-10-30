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
package pit12.platform.mixin.feature.combat.sprint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.combat.sprint.AutoSprintBinding;
import pit12.runtime.input.SprintInputBinding;

@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin implements SprintInputBinding {
    @Shadow
    protected int sprintToggleTimer;
    @Unique
    private boolean pit12$doubleTapSprinting;
    @Unique
    private boolean pit12$resumeDoubleTapSprint;

    @Override
    public boolean pit12$pauseDoubleTapSprint() {
        boolean active = pit12$resumeDoubleTapSprint
                || pit12$doubleTapSprinting && ((EntityPlayerSP) (Object) this).isSprinting();
        // Restoring the forward key would otherwise count as another tap.
        sprintToggleTimer = 0;
        pit12$resumeDoubleTapSprint = false;
        return active;
    }

    @Override
    public void pit12$resumeDoubleTapSprint() {
        pit12$resumeDoubleTapSprint = true;
    }

    // The double-tap branch also handles held sprint and Auto Sprint.
    @Inject(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;setSprinting(Z)V", ordinal = 1))
    private void pit12$markDoubleTapSprint(CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        pit12$doubleTapSprinting = !minecraft.gameSettings.keyBindSprint.isKeyDown()
                && !((AutoSprintBinding) minecraft).pit12$autoSprintEnabled();
    }

    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void pit12$updateDoubleTapSprint(boolean sprinting, CallbackInfo callback) {
        if (!sprinting)
            pit12$doubleTapSprinting = false;
        else if (pit12$resumeDoubleTapSprint)
            pit12$doubleTapSprinting = true;
    }

    @Inject(method = "onLivingUpdate", at = @At("TAIL"))
    private void pit12$finishSprintResume(CallbackInfo callback) {
        pit12$resumeDoubleTapSprint = false;
    }

    // Override both key checks while leaving the normal sprint rules in place.
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    private boolean pit12$sprintAutomatically(KeyBinding key) {
        return key.isKeyDown()
                || ((AutoSprintBinding) Minecraft.getMinecraft()).pit12$autoSprintEnabled()
                || pit12$resumeDoubleTapSprint;
    }
}
