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
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.focus.FocusRenderState;

@Mixin(value = RendererLivingEntity.class, priority = -1)
public abstract class RendererLivingEntityRedirect {
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/LayerRenderer;doRenderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V"))
    private void pit12$renderArmorDepth(LayerRenderer<EntityLivingBase> layer,
            EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float age, float yaw, float pitch, float scale) {
        boolean glint = false;
        if (FocusRenderState.opacity < 1.0F && layer instanceof LayerArmorBase) {
            for (int slot = 0; slot < 4; slot++) {
                ItemStack stack = entity.getCurrentArmor(slot);
                if (stack != null && stack.hasEffect()) {
                    glint = true;
                    break;
                }
            }
        }
        if (!glint) {
            layer.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks, age, yaw, pitch,
                    scale);
            return;
        }
        boolean previousEquipment = FocusRenderState.renderingEquipment;
        boolean previousMask = GlStateManager.depthState.maskEnabled;
        int previousFunction = GlStateManager.depthState.depthFunc;
        // Armor glint needs the base texture's depth to clip its empty pixels.
        FocusRenderState.saveDepthBuffer();
        FocusRenderState.renderingEquipment = true;
        GlStateManager.depthMask(true);
        try {
            layer.doRenderLayer(entity, limbSwing, limbSwingAmount, partialTicks, age, yaw, pitch,
                    scale);
        } finally {
            FocusRenderState.renderingEquipment = previousEquipment;
            GlStateManager.depthMask(previousMask);
            GlStateManager.depthFunc(previousFunction);
        }
    }

    @Redirect(method = "setBrightness", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTexEnvi(III)V", remap = false))
    private void pit12$keepHurtAlpha(int target, int parameter, int value) {
        // The hurt tint normally replaces the model's alpha with the texture's alpha.
        if (FocusRenderState.opacity < 1.0F && GlStateManager.activeTextureUnit == 0
                && parameter == GL13.GL_COMBINE_ALPHA && value == GL11.GL_REPLACE) {
            GL11.glTexEnvi(target, parameter, GL11.GL_MODULATE);
            GL11.glTexEnvi(target, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(target, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        } else {
            GL11.glTexEnvi(target, parameter, value);
        }
    }
}
