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
package pit12.platform.mixin.feature.autoclick;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.autoclick.AutoClickBinding;
import pit12.feature.autoclick.AutoClickFeature;
import pit12.shared.input.MinecraftActions;

// Run after Smart Block.
@Mixin(value = Minecraft.class, priority = 900)
public abstract class MinecraftMixin implements AutoClickBinding {
    @Unique
    private AutoClickFeature pit12$autoClick;

    @Override
    public void pit12$bindAutoClick(AutoClickFeature feature) {
        pit12$autoClick = feature;
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0))
    private void pit12$autoClickInput(CallbackInfo callback) {
        if (pit12$autoClick != null) {
            pit12$autoClick.inputTick();
        }
    }

    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;clickMouse()V"))
    private void pit12$autoClickAttack(Minecraft minecraft) {
        if (pit12$autoClick == null || !pit12$autoClick.leftClicking()) {
            ((MinecraftActions) minecraft).pit12$leftClick();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;rightClickMouse()V"))
    private void pit12$autoClickUse(Minecraft minecraft) {
        if (pit12$autoClick == null || !pit12$autoClick.rightClicking()) {
            ((MinecraftActions) minecraft).pit12$rightClick();
        }
    }
}
