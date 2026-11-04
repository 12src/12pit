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
package pit12.feature.render.focus;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.KeybindSetting;

public final class FocusConfig extends FeatureConfig {
    final ChoiceSetting mode;
    final IntegerSetting opacity;
    final BooleanSetting targetPriority;
    final KeybindSetting toggleKey;
    final KeybindSetting cycleKey;
    final ChoiceSetting equipmentMatch;
    final BooleanSetting chestplate;
    final BooleanSetting boots;
    final BooleanSetting leggings;
    final BooleanSetting helmet;

    public FocusConfig() {
        super("focus", source("Focus"), ConfigCategory.RENDER,
                source("Keeps selected players visible and fades other players."), true, false);
        subcategory("general", source("General"));
        mode = choiceSetting("mode", source("Mode"),
                source("Chooses which players keep their normal opacity."), 0,
                new ChoiceSetting.Choice(0, source("Non-Diamond")),
                new ChoiceSetting.Choice(1, "TDM"), new ChoiceSetting.Choice(2, source("Enemies")),
                new ChoiceSetting.Choice(3, source("TDM + Non-Diamond")));
        opacity = integerSliderSetting("opacity", source("Non-target opacity"),
                source("Opacity of other players. Zero hides their models and names."), 0, 0, 100,
                1);
        targetPriority = booleanSetting("target_priority", source("Hurt time"), source(
                "Sets a high hurt time for non-targets so other clients using hurt time targeting are less likely to select them."),
                false);
        toggleKey = keybindSetting("toggle_key", source("Toggle key"),
                source("Toggles Focus without changing the selected mode."), Keyboard.KEY_NONE);
        cycleKey = keybindSetting("cycle_key", source("Cycle mode key"),
                source("Cycles modes only while Focus is enabled."), Keyboard.KEY_NONE);
        subcategory("diamond_equipment", source("Diamond equipment"));
        equipmentMatch = choiceSetting("equipment_match", source("Match rule"),
                source("AND: all selected pieces. OR: any selected piece."), 1,
                new ChoiceSetting.Choice(0, "AND"), new ChoiceSetting.Choice(1, "OR"));
        chestplate = booleanSetting("chestplate", source("Chestplate"),
                source("Checks for a diamond chestplate."), true);
        boots = booleanSetting("boots", source("Boots"), source("Checks for diamond boots."), true);
        leggings = booleanSetting("leggings", source("Leggings"),
                source("Checks for diamond leggings."), true);
        helmet = booleanSetting("helmet", source("Helmet"),
                source("Checks for a diamond or golden helmet."), true);
    }
}
