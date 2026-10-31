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
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void pit12$rotateFreeCamera(float yawChange, float pitchChange, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if ((Object) this != minecraft.thePlayer) {
            return;
        }
        FreeCameraFeature feature = ((FreeCameraBinding) minecraft).pit12$freeCamera();
        if (feature != null && feature.isActive()) {
            feature.camera().setAngles(yawChange, pitchChange);
            callback.cancel();
        }
    }
}
