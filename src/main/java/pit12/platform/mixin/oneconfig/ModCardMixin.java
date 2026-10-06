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
import cc.polyfrost.oneconfig.gui.animations.ColorAnimation;
import cc.polyfrost.oneconfig.gui.elements.ModCard;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pit12.platform.oneconfig.OneConfigAdapter;

@Pseudo
@Mixin(targets = "cc.polyfrost.oneconfig.gui.elements.ModCard", remap = false)
public abstract class ModCardMixin {
    @Shadow
    @Final
    private Mod modData;
    @Shadow
    @Final
    private ColorAnimation colorToggle;
    @Shadow
    private boolean active;

    // Native cards cache their state; 12pit can also change it through profiles and the Web UI.
    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    private void pit12$syncEnabled(CallbackInfo callback) {
        if (modData.config instanceof OneConfigAdapter.View && active != modData.config.enabled) {
            active = modData.config.enabled;
            ((ModCard) (Object) this).setToggled(active);
            colorToggle.setPalette(active ? ColorPalette.PRIMARY : ColorPalette.SECONDARY);
        }
    }
}
