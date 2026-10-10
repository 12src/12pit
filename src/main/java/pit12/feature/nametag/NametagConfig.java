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
package pit12.feature.nametag;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.item.PitEnchantmentFormat;

public final class NametagConfig extends FeatureConfig {
    private final ChoiceSetting enchantmentFormat;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showLeggings;
    private final IntegerSetting displayDistance;
    private final IntegerSetting upwardOffset;

    public NametagConfig() {
        super("nametag", source("Nametag"), new ConfigCategory("render", source("Render"), 100),
                source("Shows Pit enchantments above player nametags."));
        subsubcategory("enchantments", source("Enchantments"));
        enchantmentFormat = choiceSetting("enchantment_format", source("Enchantment format"),
                source("Controls how enchantment names and levels are shown."),
                PitEnchantmentFormat.BOLD_LEVELS, PitEnchantmentFormat.choices());
        showHeldItem = booleanSetting("show_held_item", source("Show held item enchantments"),
                source("Shows the held item's Pit enchantments."), true);
        showLeggings = booleanSetting("show_leggings", source("Show leggings enchantments"),
                source("Shows the leggings' Pit enchantments."), true);
        subsubcategory("display", source("Display"));
        displayDistance = integerSliderSetting("display_distance", source("Display distance"),
                source("Shows enchantments within this distance, in blocks."), 32, 1, 64, 1);
        upwardOffset = integerSliderSetting("upward_offset", source("Upward offset"),
                source("Moves enchantments above the nametag, in hundredths of a block."), 36, 20,
                40, 1);
    }

    public int enchantmentFormat() {
        return enchantmentFormat.get();
    }

    public boolean showHeldItem() {
        return showHeldItem.get();
    }

    public boolean showLeggings() {
        return showLeggings.get();
    }

    public int displayDistance() {
        return displayDistance.get();
    }

    public int upwardOffset() {
        return upwardOffset.get();
    }
}
