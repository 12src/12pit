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
package pit12.feature.render.focus.mixin;

import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pit12.feature.render.focus.FocusRenderState;

// Run after Lunar adds its item color writer at priority 200.
@SuppressWarnings("target")
@Mixin(value = RenderItem.class, priority = 100)
public abstract class LunarRenderItemMixin {
    // Match the generated writer by signature. HEAD permits this lower-priority hook.
    @Dynamic("Lunar's ARGB item color writer")
    @ModifyVariable(method = "(Lnet/minecraft/client/renderer/WorldRenderer;I)V", at = @At("HEAD"),
            argsOnly = true, remap = false, require = 1)
    private int pit12$fadeItemColor(int color) {
        if (FocusRenderState.opacity == 1.0F)
            return color;
        return FocusRenderState.glintColor(
                color & 0xFFFFFF | (int) ((color >>> 24) * FocusRenderState.opacity) << 24);
    }
}
