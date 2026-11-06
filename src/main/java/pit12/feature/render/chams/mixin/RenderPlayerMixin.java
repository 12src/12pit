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
package pit12.feature.render.chams.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.render.chams.ChamsBinding;
import pit12.feature.render.chams.ChamsConfig;
import pit12.shared.performance.HotPath;

@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin extends RendererLivingEntity<AbstractClientPlayer>
        implements ChamsBinding {
    @Unique
    private ChamsConfig pit12$chamsConfig;

    protected RenderPlayerMixin(RenderManager renderManager, ModelBase model, float shadowSize) {
        super(renderManager, model, shadowSize);
    }

    @Override
    public void pit12$bindChamsConfig(ChamsConfig config) {
        pit12$chamsConfig = config;
    }

    @Redirect(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"))
    @HotPath
    private void pit12$renderThroughBlocks(RendererLivingEntity<AbstractClientPlayer> renderer,
            EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (pit12$chamsConfig == null || !pit12$chamsConfig.enabled() || minecraft.thePlayer == null
                || player == minecraft.thePlayer || player.worldObj != minecraft.theWorld) {
            super.doRender(player, x, y, z, yaw, partialTicks);
            return;
        }
        boolean previousEnabled = GlStateManager.polygonOffsetState.polygonOffsetFill.currentState;
        float previousFactor = GlStateManager.polygonOffsetState.factor;
        float previousUnits = GlStateManager.polygonOffsetState.units;
        // Armor glint still needs depth testing.
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(1.0F, -1000000.0F);
        try {
            super.doRender(player, x, y, z, yaw, partialTicks);
        } finally {
            GlStateManager.doPolygonOffset(previousFactor, previousUnits);
            if (previousEnabled) {
                GlStateManager.enablePolygonOffset();
            } else {
                GlStateManager.disablePolygonOffset();
            }
        }
    }
}
