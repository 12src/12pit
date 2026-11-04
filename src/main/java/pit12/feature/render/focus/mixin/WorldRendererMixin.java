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

import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pit12.feature.render.focus.FocusRenderState;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @ModifyVariable(method = "color(IIII)Lnet/minecraft/client/renderer/WorldRenderer;",
            at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int pit12$fadeVertexAlpha(int alpha) {
        return FocusRenderState.opacity == 1.0F ? alpha : (int) (alpha * FocusRenderState.opacity);
    }

    @ModifyVariable(method = "putColorRGBA", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int pit12$fadeQuadAlpha(int alpha) {
        return FocusRenderState.opacity == 1.0F ? alpha : (int) (alpha * FocusRenderState.opacity);
    }

    @ModifyVariable(method = "putColor4", at = @At("HEAD"), argsOnly = true)
    private int pit12$fadeItemGlint(int color) {
        return FocusRenderState.glintColor(color);
    }

    @ModifyArg(
            method = "putColorMultiplier", at = @At(value = "INVOKE",
                    target = "Ljava/nio/IntBuffer;put(II)Ljava/nio/IntBuffer;", remap = false),
            index = 1)
    private int pit12$fadeTintedQuad(int color) {
        return FocusRenderState.vertexColor(color);
    }
}
