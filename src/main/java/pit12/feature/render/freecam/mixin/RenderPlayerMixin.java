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
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin {
    // RenderPlayer skips the local body when another entity owns the view.
    @Redirect(method = "doRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isUser()Z"))
    private boolean pit12$renderOwnBodyInFreeCamera(AbstractClientPlayer player) {
        FreeCameraFeature feature =
                ((FreeCameraBinding) Minecraft.getMinecraft()).pit12$freeCamera();
        if (feature != null && feature.isActive() && player == Minecraft.getMinecraft().thePlayer) {
            return false;
        }
        return player.isUser();
    }
}
