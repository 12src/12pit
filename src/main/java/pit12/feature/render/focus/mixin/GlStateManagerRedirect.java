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

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.focus.FocusRenderState;

@Mixin(value = GlStateManager.class, priority = -1)
public abstract class GlStateManagerRedirect {
    @Redirect(method = "color(FFFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false))
    private static void pit12$fadeColor(float red, float green, float blue, float alpha) {
        FocusRenderState.color(red, green, blue, alpha);
    }

    @ModifyArg(method = "alphaFunc", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V", remap = false), index = 1)
    private static float pit12$fadeAlphaThreshold(float reference) {
        return reference * FocusRenderState.opacity;
    }

    @Redirect(method = "disableBlend", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager$BooleanState;setDisabled()V"))
    private static void pit12$keepBlending(GlStateManager.BooleanState state) {
        if (FocusRenderState.opacity == 1.0F) {
            state.setDisabled();
        } else {
            state.setEnabled();
            // Armor glint disables blending without restoring its additive blend function.
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}
