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
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.focus.FocusRenderState;

@Mixin(value = RenderItem.class, priority = -1)
public abstract class RenderItemRedirect {
    @Redirect(method = "renderItemModelTransform", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V"))
    private void pit12$renderItemDepth(RenderItem renderer, ItemStack stack, IBakedModel model) {
        if (FocusRenderState.opacity == 1.0F || !stack.hasEffect()) {
            renderer.renderItem(stack, model);
            return;
        }
        boolean previousEquipment = FocusRenderState.renderingEquipment;
        boolean previousMask = GlStateManager.depthState.maskEnabled;
        int previousFunction = GlStateManager.depthState.depthFunc;
        // Glint needs the base item's depth to mask transparent texture pixels.
        FocusRenderState.saveDepthBuffer();
        FocusRenderState.renderingEquipment = true;
        GlStateManager.depthMask(true);
        try {
            renderer.renderItem(stack, model);
        } finally {
            FocusRenderState.renderingEquipment = previousEquipment;
            GlStateManager.depthMask(previousMask);
            GlStateManager.depthFunc(previousFunction);
        }
    }
}
