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

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class TooltipConfig extends FeatureConfig {
    private final BooleanSetting showEnchantments;
    private final IntegerSetting upwardOffset;

    public TooltipConfig() {
        super("tooltip", "Held Item Tooltip", new ConfigCategory("render", "Render", 100),
                "Customizes the held item tooltip.");
        showEnchantments = booleanSetting("show_enchantments", "Enchantments",
                "Replaces the held item name with known Pit enchantments.", true);
        upwardOffset = integerSliderSetting("upward_offset", "Upward offset",
                "Moves the held item tooltip upward by this many pixels.", 0, 0, 30, 1);
    }

    public boolean showEnchantments() {
        return showEnchantments.get().booleanValue();
    }

    public int upwardOffset() {
        return upwardOffset.get().intValue();
    }
}
