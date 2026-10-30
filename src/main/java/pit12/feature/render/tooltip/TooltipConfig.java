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
package pit12.feature.tooltip;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class TooltipConfig extends FeatureConfig {
    private final BooleanSetting showEnchantments;
    private final BooleanSetting showGem;
    private final IntegerSetting upwardOffset;

    public TooltipConfig() {
        super("tooltip", source("Held Item Tooltip"), ConfigCategory.RENDER,
                source("Changes the text and position of the held item tooltip."));
        showEnchantments = booleanSetting("show_enchantments",
                source("Show enchantments instead of item name"),
                source("Shows Pit enchantments in place of the held item name."), true);
        showGem = booleanSetting("show_gem", source("Show gem"),
                source("Shows a green diamond on gemmed items."), true);
        upwardOffset = integerSliderSetting("upward_offset", source("Upward offset (pixels)"),
                source("Moves the held item tooltip upward."), 0, 0, 30, 1);
    }

    public boolean showEnchantments() {
        return showEnchantments.get();
    }

    public boolean showGem() {
        return showGem.get();
    }

    public int upwardOffset() {
        return upwardOffset.get();
    }
}
