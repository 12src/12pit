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
package pit12.platform.mixin.oneconfig;

import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.gui.pages.ModsPage;
import cc.polyfrost.oneconfig.utils.InputHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.platform.oneconfig.OneConfigAdapter;

@Pseudo
@Mixin(targets = "cc.polyfrost.oneconfig.gui.pages.SubModsPage", remap = false)
public abstract class SubModsPageMixin extends ModsPage {
    @Shadow
    public Mod parentMod;

    // SubModsPage hides the category bar that ModsPage already draws.
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void pit12$drawFeatureCards(long vg, int x, int y, InputHandler inputHandler,
            CallbackInfo callback) {
        if (parentMod.config instanceof OneConfigAdapter.RootView) {
            super.draw(vg, x, y, inputHandler);
            callback.cancel();
        }
    }

    @Inject(method = "drawStatic", at = @At("HEAD"), cancellable = true, remap = false)
    private void pit12$drawFeatureCategories(long vg, int x, int y, InputHandler inputHandler,
            CallbackInfoReturnable<Integer> callback) {
        if (parentMod.config instanceof OneConfigAdapter.RootView) {
            callback.setReturnValue(super.drawStatic(vg, x, y, inputHandler));
        }
    }

    @Inject(method = "getMaxScrollHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private void pit12$featureScrollHeight(CallbackInfoReturnable<Integer> callback) {
        if (parentMod.config instanceof OneConfigAdapter.RootView) {
            callback.setReturnValue(super.getMaxScrollHeight());
        }
    }
}
