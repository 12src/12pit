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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.focus.FocusRenderPass;
import pit12.shared.performance.HotPath;

@Mixin(value = RenderGlobal.class, priority = -1)
public abstract class RenderGlobalMixin {
    @Shadow
    @Final
    private Minecraft mc;
    @Unique
    private FocusRenderPass pit12$focusPass;

    // Shader shadow passes call RenderGlobal directly, without EntityRenderer.renderWorldPass.
    @Inject(method = "renderEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderManager;setRenderPosition(DDD)V",
            shift = At.Shift.AFTER))
    @HotPath
    private void pit12$beginPlayerPass(CallbackInfo ci) {
        if (!FocusRenderPass.enabled)
            return;
        if (pit12$focusPass == null)
            pit12$focusPass = new FocusRenderPass();
        pit12$focusPass.begin();
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;disableLightmap()V"))
    private void pit12$finishPlayerPass(CallbackInfo ci) {
        // Tile batches and their damage overlays must reach the background first.
        if (FocusRenderPass.current != null) {
            mc.entityRenderer.enableLightmap();
            FocusRenderPass.current.draw(null, false);
        }
    }
}
