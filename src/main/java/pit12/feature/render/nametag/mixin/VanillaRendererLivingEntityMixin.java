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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.render.nametag.NametagNamesBinding;

@Mixin(RendererLivingEntity.class)
public abstract class VanillaRendererLivingEntityMixin {
    @Unique
    private EntityPlayer pit12$nametagPlayer;

    @Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"))
    private void pit12$capturePlayer(EntityLivingBase entity, double x, double y, double z,
            CallbackInfo ci) {
        if (((NametagNamesBinding) Minecraft.getMinecraft()).pit12$nametagNames() != null) {
            pit12$nametagPlayer = entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
        }
    }

    @ModifyVariable(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",
            at = @At("STORE"), ordinal = 0)
    private String pit12$displayName(String original) {
        EntityPlayer player = pit12$nametagPlayer;
        if (player == null) {
            return original;
        }
        pit12$nametagPlayer = null;
        return original.concat(((NametagNamesBinding) Minecraft.getMinecraft()).pit12$nametagNames()
                .apply(player));
    }
}
