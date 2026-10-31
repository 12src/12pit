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
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.freecam.FreeCameraBinding;
import pit12.feature.render.freecam.FreeCameraFeature;

@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeMixin {
    @Redirect(method = {"renderAir", "renderHealth", "renderFood", "renderHealthMount"},
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;getRenderViewEntity()Lnet/minecraft/entity/Entity;",
                    remap = true))
    private Entity pit12$showPlayerStats(Minecraft minecraft) {
        FreeCameraFeature feature = ((FreeCameraBinding) minecraft).pit12$freeCamera();
        return feature != null && feature.isActive() ? minecraft.thePlayer
                : minecraft.getRenderViewEntity();
    }
}
