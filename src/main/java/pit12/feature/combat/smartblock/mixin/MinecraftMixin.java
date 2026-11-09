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
package pit12.feature.combat.smartblock.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.feature.combat.smartblock.SmartBlockBinding;
import pit12.feature.combat.smartblock.SmartBlockFeature;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements SmartBlockBinding {
    @Unique
    private SmartBlockFeature pit12$smartBlock;

    @Override
    public void pit12$bindSmartBlock(SmartBlockFeature feature) {
        pit12$smartBlock = feature;
    }

    @Override
    public SmartBlockFeature pit12$smartBlock() {
        return pit12$smartBlock;
    }

    // Slot 4 holds the wheel delta before the spectator and hotbar paths.
    @ModifyVariable(method = "runTick", at = @At(value = "STORE", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false)),
            index = 4, require = 1)
    private int pit12$smartBlockScroll(int wheel) {
        if (wheel == 0 || pit12$smartBlock == null || !pit12$smartBlock.slotLocked()
                || ((Minecraft) (Object) this).thePlayer.isSpectator()) {
            return wheel;
        }
        return 0;
    }

    // Run after key and hotbar input, before attacks and item use.
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z", ordinal = 0))
    private void pit12$smartBlockInput(CallbackInfo callback) {
        if (pit12$smartBlock != null) {
            pit12$smartBlock.inputTick();
        }
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"))
    private void pit12$cancelSmartBlock(GuiScreen screen, CallbackInfo callback) {
        if (screen != null && pit12$smartBlock != null) {
            pit12$smartBlock.cancel();
        }
    }

    @Inject(method = "middleClickMouse", at = @At("HEAD"), cancellable = true)
    private void pit12$smartBlockPickBlock(CallbackInfo callback) {
        if (pit12$smartBlock != null && pit12$smartBlock.slotLocked()) {
            callback.cancel();
        }
    }
}
