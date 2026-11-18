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
package pit12.feature.combat.enhancedmlb.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pit12.feature.combat.enhancedmlb.EnhancedMlbBinding;
import pit12.feature.combat.enhancedmlb.EnhancedMlbConfig;
import pit12.runtime.item.PitEnchantment;
import pit12.runtime.item.PitEnchantmentReader;

@Mixin(value = Minecraft.class, priority = -1)
public abstract class MinecraftRedirect implements EnhancedMlbBinding {
    @Unique
    private EnhancedMlbConfig pit12$enhancedMlb;

    @Override
    public void pit12$bindEnhancedMlb(EnhancedMlbConfig config) {
        pit12$enhancedMlb = config;
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;onStoppedUsingItem(Lnet/minecraft/entity/player/EntityPlayer;)V"),
            require = 1)
    private void pit12$releaseBow(PlayerControllerMP controller, EntityPlayer player) {
        if (pit12$enhancedMlb != null && pit12$enhancedMlb.enabled()
                && player.getItemInUseDuration() < 3) {
            ItemStack bow = player.getItemInUse();
            if (bow != null && bow == player.getHeldItem() && bow.getItem() instanceof ItemBow
                    && PitEnchantmentReader.contains(bow, PitEnchantment.Mega_Longbow)) {
                // Vanilla checks again next tick while the use key is up.
                return;
            }
        }
        controller.onStoppedUsingItem(player);
    }
}
