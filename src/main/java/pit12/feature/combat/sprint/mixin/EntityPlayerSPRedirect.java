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
package pit12.feature.combat.sprint.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.combat.sprint.AutoSprintBinding;
import pit12.runtime.input.SprintInputBinding;

@Mixin(value = EntityPlayerSP.class, priority = -1)
public abstract class EntityPlayerSPRedirect {
    // Override both key checks while leaving the normal sprint rules in place.
    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    private boolean pit12$sprintAutomatically(KeyBinding key) {
        return key.isKeyDown()
                || ((AutoSprintBinding) Minecraft.getMinecraft()).pit12$autoSprintEnabled()
                || ((SprintInputBinding) this).pit12$resumingDoubleTapSprint();
    }
}
