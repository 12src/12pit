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
package pit12.feature.render.nametag.mixin;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.feature.render.nametag.LunarNametagSource;
import pit12.platform.lunar.LunarNametagText;

@Mixin(value = RendererLivingEntity.class, priority = 100)
public abstract class LunarRendererLivingEntityMixin implements LunarNametagSource {
    @Shadow
    public abstract void renderName(EntityLivingBase entity, double x, double y, double z);

    @Unique
    private EntityPlayer pit12$nametagPlayer;
    @Unique
    private Object pit12$nametagComponent;

    @Override
    public String pit12$playerNametag(EntityPlayer player, double x, double y, double z,
            Name cachedName) {
        // Let Lunar decide whether to show the name before intercepting the draw calls.
        pit12$nametagPlayer = player;
        try {
            renderName(player, x, y, z);
            return pit12$nametagComponent == null ? null
                    : cachedName.format(pit12$nametagComponent, LunarNametagText::serialize);
        } finally {
            pit12$nametagPlayer = null;
            pit12$nametagComponent = null;
        }
    }

    @Unique
    private boolean pit12$captureName(Entity entity, Object component, boolean playerName) {
        if (entity != pit12$nametagPlayer) {
            return false;
        }
        if (playerName && pit12$nametagComponent == null) {
            pit12$nametagComponent = component;
        }
        return true;
    }

    @Dynamic("Lunar's nametag draw method")
    @Inject(method = "impl$drawLabel", at = @At("HEAD"), cancellable = true, remap = false)
    private void pit12$collectNametagLine(EntityLivingBase entity, @Coerce Object component,
            double x, double y, double z, boolean playerName, CallbackInfoReturnable<Double> ci) {
        if (pit12$captureName(entity, component, playerName)) {
            ci.setReturnValue(y);
        }
    }

    @Dynamic("Lunar's sneaking nametag draw method")
    @Inject(method = "impl$renderLabelSneaking", at = @At("HEAD"), cancellable = true,
            remap = false)
    private void pit12$collectSneakingNametagLine(Entity entity, @Coerce Object component, double x,
            double y, double z, boolean playerName, CallbackInfo ci) {
        if (pit12$captureName(entity, component, playerName)) {
            ci.cancel();
        }
    }
}
