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
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements FreeCameraBinding {
    @Unique
    private FreeCameraFeature pit12$freeCamera;

    @Override
    public void pit12$bindFreeCamera(FreeCameraFeature feature) {
        pit12$freeCamera = feature;
    }

    @Override
    public FreeCameraFeature pit12$freeCamera() {
        return pit12$freeCamera;
    }

    @Inject(method = {"clickMouse", "rightClickMouse", "middleClickMouse",
            "sendClickBlockToController"}, at = @At("HEAD"), cancellable = true)
    private void pit12$blockFreeCameraActions(CallbackInfo callback) {
        if (pit12$freeCamera != null && pit12$freeCamera.isActive()) {
            callback.cancel();
        }
    }

    @ModifyArg(method = "runGameLoop", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/audio/SoundHandler;setListener(Lnet/minecraft/entity/player/EntityPlayer;F)V"),
            index = 0)
    private EntityPlayer pit12$listenFromFreeCamera(EntityPlayer player) {
        return pit12$freeCamera != null && pit12$freeCamera.isActive() ? pit12$freeCamera.camera()
                : player;
    }
}
