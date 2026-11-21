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
package pit12.feature.render.cosmetic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.cosmetic.CosmeticBinding;
import pit12.feature.render.cosmetic.CosmeticRenderer;
import pit12.shared.performance.HotPath;

@Mixin(RendererLivingEntity.class)
public abstract class RendererLivingEntityMixin {
    @Shadow
    protected ModelBase mainModel;

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            at = @At("RETURN"))
    @HotPath
    private void pit12$renderCosmetic(EntityLivingBase entity, float limbSwing,
            float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo callback) {
        if (!(entity instanceof EntityPlayer) || !(mainModel instanceof ModelBiped)) {
            return;
        }
        CosmeticRenderer renderer =
                ((CosmeticBinding) Minecraft.getMinecraft()).pit12$cosmeticRenderer();
        if (renderer != null) {
            renderer.render((EntityPlayer) entity, (ModelBiped) mainModel, scale);
        }
    }
}
