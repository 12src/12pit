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
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.focus.FocusRenderPass;
import pit12.feature.render.focus.FocusRenderState;
import pit12.feature.render.focus.api.FocusOpacity;
import pit12.shared.performance.HotPath;

@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {
    @Shadow
    private boolean renderOutlines;
    @Unique
    private FocusRenderState pit12$focusState;

    @Redirect(method = "doRenderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    @HotPath
    private void pit12$renderPlayer(Render<Entity> renderer, Entity entity, double x, double y,
            double z, float yaw, float partialTicks) {
        pit12$render(renderer, entity, x, y, z, yaw, partialTicks, false);
    }

    @Redirect(method = "doRenderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void pit12$renderShadowAndFire(Render<Entity> renderer, Entity entity, double x,
            double y, double z, float yaw, float partialTicks) {
        pit12$render(renderer, entity, x, y, z, yaw, partialTicks, true);
    }

    @Unique
    private void pit12$render(Render<Entity> renderer, Entity entity, double x, double y, double z,
            float yaw, float partialTicks, boolean shadow) {
        float opacity = FocusRenderState.opacity == 1.0F && entity instanceof EntityPlayer
                ? ((FocusOpacity) Minecraft.getMinecraft())
                        .pit12$focusOpacity((EntityPlayer) entity)
                : 1.0F;
        if (opacity == 0.0F)
            return;
        if (opacity < 1.0F && !renderOutlines && FocusRenderPass.current != null) {
            if (!shadow) {
                FocusRenderPass.current.add((RenderManager) (Object) this, entity, x, y, z, yaw,
                        partialTicks, opacity);
            }
            return;
        }
        if (opacity < 1.0F) {
            if (pit12$focusState == null)
                pit12$focusState = new FocusRenderState();
            pit12$focusState.begin(opacity);
        }
        try {
            if (shadow)
                renderer.doRenderShadowAndFire(entity, x, y, z, yaw, partialTicks);
            else
                renderer.doRender(entity, x, y, z, yaw, partialTicks);
        } finally {
            if (opacity < 1.0F)
                pit12$focusState.end();
        }
    }
}
