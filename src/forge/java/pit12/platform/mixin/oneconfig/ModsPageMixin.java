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
import cc.polyfrost.oneconfig.gui.elements.BasicButton;
import cc.polyfrost.oneconfig.gui.elements.ModCard;
import cc.polyfrost.oneconfig.gui.pages.ModsPage;
import cc.polyfrost.oneconfig.gui.pages.SubModsPage;
import cc.polyfrost.oneconfig.internal.config.core.ConfigCore;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pit12.platform.oneconfig.OneConfigAdapter;
import pit12.runtime.config.ConfigCategory;

@Pseudo
@Mixin(targets = "cc.polyfrost.oneconfig.gui.pages.ModsPage", remap = false)
public abstract class ModsPageMixin {
    @Shadow
    @Final
    private ArrayList<BasicButton> modCategories;
    @Unique
    private Map<String, BasicButton> pit12$featureCategories;

    @Shadow
    private void unselect(int index) {}

    @Inject(method = "reloadMods", at = @At("TAIL"), remap = false)
    private void pit12$addFeatureCategories(CallbackInfo callback) {
        if (pit12$featureCategories != null || !((Object) this instanceof SubModsPage)) {
            return;
        }
        Mod parent = ((SubModsPage) (Object) this).parentMod;
        // SubModsPage assigns its parent after the first reload.
        if (parent == null || !(parent.config instanceof OneConfigAdapter.RootView)) {
            return;
        }
        pit12$featureCategories = new LinkedHashMap<>();
        modCategories.subList(1, modCategories.size()).clear();
        for (Mod mod : ConfigCore.subMods.get(parent)) {
            ConfigCategory category = ((OneConfigAdapter.View) mod.config).feature.category();
            if (category == null || pit12$featureCategories.containsKey(category.id())) {
                continue;
            }
            BasicButton button =
                    new BasicButton(0, BasicButton.SIZE_32, category.originalDisplayName(),
                            BasicButton.ALIGNMENT_CENTER, ColorPalette.SECONDARY);
            button.setToggleable(true);
            modCategories.add(button);
            pit12$featureCategories.put(category.id(), button);
        }
        for (int index = 0; index < modCategories.size(); index++) {
            int selected = index;
            modCategories.get(index).setClickAction(() -> {
                unselect(selected);
                ((ModsPage) (Object) this).finishUpAndClose();
            });
        }
    }

    @Inject(method = "drawStatic", at = @At("HEAD"), remap = false)
    private void pit12$sizeFeatureCategories(long vg, int x, int y, InputHandler inputHandler,
            CallbackInfoReturnable<Integer> callback) {
        if (pit12$featureCategories == null) {
            return;
        }
        for (BasicButton button : pit12$featureCategories.values()) {
            if (button.getWidth() == 0) {
                button.setWidth((int) Math.ceil(
                        NanoVGHelper.INSTANCE.getTextWidth(vg, button.getText(), 12f, Fonts.MEDIUM))
                        + 24);
            }
        }
    }

    @Inject(method = "inSelection", at = @At("HEAD"), cancellable = true, remap = false)
    private void pit12$filterFeatureCategory(ModCard card,
            CallbackInfoReturnable<Boolean> callback) {
        if (pit12$featureCategories == null) {
            return;
        }
        if (modCategories.get(0).isToggled()) {
            callback.setReturnValue(true);
            return;
        }
        ConfigCategory category =
                ((OneConfigAdapter.View) card.getModData().config).feature.category();
        BasicButton button = category == null ? null : pit12$featureCategories.get(category.id());
        callback.setReturnValue(button != null && button.isToggled());
    }
}
