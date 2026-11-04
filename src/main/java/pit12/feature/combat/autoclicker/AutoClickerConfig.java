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
package pit12.feature.combat.autoclicker;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.KeybindSetting;

public final class AutoClickerConfig extends FeatureConfig {
    private final BooleanSetting leftClick;
    private final BooleanSetting leftWeaponsOnly;
    private final BooleanSetting leftHoldingOnly;
    private final IntegerSetting leftCpsMin;
    private final IntegerSetting leftCpsMax;
    private final KeybindSetting leftToggleKey;
    private final BooleanSetting rightClick;
    private final BooleanSetting rightBlocksOnly;
    private final BooleanSetting rightHoldingOnly;
    private final IntegerSetting rightCpsMin;
    private final IntegerSetting rightCpsMax;
    private final KeybindSetting rightToggleKey;
    private final BooleanSetting cakeClick;
    private final ChoiceSetting cakeMode;
    private final BooleanSetting cakeHoldingOnly;
    private final IntegerSetting cakeCpsMin;
    private final IntegerSetting cakeCpsMax;

    public AutoClickerConfig() {
        super("autoclicker", source("Auto Clicker"), ConfigCategory.COMBAT,
                source("Clicks the left and right mouse buttons automatically."), true, false);
        subcategory("left", source("Left click"));
        leftClick = booleanSetting("left_click", source("Enabled"),
                source("Clicks the left mouse button automatically."), true);
        leftWeaponsOnly = booleanSetting("left_weapons_only", source("Weapons only"),
                source("Only clicks while holding a sword, axe, or shovel."), false);
        leftHoldingOnly = booleanSetting("left_holding_only", source("Hold to click"),
                source("Clicks only while the left mouse button is held."), true);
        leftCpsMin = integerSliderSetting("left_cps_min", source("Click rate min (CPS)"),
                source("The lowest target click rate."), 8, 1, 20, 1);
        leftCpsMax = integerSliderSetting("left_cps_max", source("Click rate max (CPS)"),
                source("The highest target click rate."), 12, 1, 20, 1);
        leftToggleKey = keybindSetting("left_toggle_key", source("Toggle key"),
                source("Toggles left click automation."), Keyboard.KEY_NONE);
        subcategory("right", source("Right click"));
        rightClick = booleanSetting("right_click", source("Enabled"),
                source("Clicks the right mouse button automatically."), false);
        rightBlocksOnly = booleanSetting("right_blocks_only", source("Blocks only"),
                source("Only clicks while holding a placeable block."), false);
        rightHoldingOnly = booleanSetting("right_holding_only", source("Hold to click"),
                source("Clicks only while the right mouse button is held."), true);
        rightCpsMin = integerSliderSetting("right_cps_min", source("Click rate min (CPS)"),
                source("The lowest target click rate."), 8, 1, 20, 1);
        rightCpsMax = integerSliderSetting("right_cps_max", source("Click rate max (CPS)"),
                source("The highest target click rate."), 12, 1, 20, 1);
        rightToggleKey = keybindSetting("right_toggle_key", source("Toggle key"),
                source("Toggles right click automation."), Keyboard.KEY_NONE);
        subcategory("cake", source("Cake"));
        cakeClick = booleanSetting("cake_click", source("Enabled"),
                source("Clicks cakes while looking at them."), false);
        cakeMode = choiceSetting("cake_click_mode", source("Click button"),
                source("Which button is used for cake."), 0,
                new ChoiceSetting.Choice(0, source("Left click")),
                new ChoiceSetting.Choice(1, source("Right click")));
        cakeHoldingOnly = booleanSetting("cake_click_holding_only", source("Hold to click"),
                source("Clicks only while the matching button is held."), true);
        cakeCpsMin = integerSliderSetting("cake_cps_min", source("Click rate min (CPS)"),
                source("The lowest target click rate."), 8, 1, 20, 1);
        cakeCpsMax = integerSliderSetting("cake_cps_max", source("Click rate max (CPS)"),
                source("The highest target click rate."), 12, 1, 20, 1);
    }

    public BooleanSetting leftClick() {
        return leftClick;
    }

    public BooleanSetting leftWeaponsOnly() {
        return leftWeaponsOnly;
    }

    public BooleanSetting leftHoldingOnly() {
        return leftHoldingOnly;
    }

    public IntegerSetting leftCpsMin() {
        return leftCpsMin;
    }

    public IntegerSetting leftCpsMax() {
        return leftCpsMax;
    }

    public KeybindSetting leftToggleKey() {
        return leftToggleKey;
    }

    public BooleanSetting rightClick() {
        return rightClick;
    }

    public BooleanSetting rightBlocksOnly() {
        return rightBlocksOnly;
    }

    public BooleanSetting rightHoldingOnly() {
        return rightHoldingOnly;
    }

    public IntegerSetting rightCpsMin() {
        return rightCpsMin;
    }

    public IntegerSetting rightCpsMax() {
        return rightCpsMax;
    }

    public KeybindSetting rightToggleKey() {
        return rightToggleKey;
    }

    public BooleanSetting cakeClick() {
        return cakeClick;
    }

    public ChoiceSetting cakeMode() {
        return cakeMode;
    }

    public BooleanSetting cakeHoldingOnly() {
        return cakeHoldingOnly;
    }

    public IntegerSetting cakeCpsMin() {
        return cakeCpsMin;
    }

    public IntegerSetting cakeCpsMax() {
        return cakeCpsMax;
    }
}
