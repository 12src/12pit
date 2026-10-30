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
package pit12.feature.render.nametag;

import static pit12.runtime.languages.Languages.source;

import pit12.feature.render.timers.api.PlayerTimers;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.item.PitEnchantmentFormat;

public final class NametagConfig extends FeatureConfig {
    private final BooleanSetting nickMarker;
    private final BooleanSetting realNames;
    private final BooleanSetting pinTimer;
    private final BooleanSetting venomTimer;
    private final BooleanSetting jumpBoostTimer;
    private final BooleanSetting telebowTimer;
    private final ChoiceSetting enchantmentFormat;
    private final BooleanSetting showGem;
    private final BooleanSetting showHeldItem;
    private final BooleanSetting showLeggings;
    private final IntegerSetting upwardOffset;

    public NametagConfig() {
        super("nametag", source("Nametag"), ConfigCategory.RENDER,
                source("Adds real names, Pit enchantments, and timers to player nametags."));
        subsubcategory("names", "Denick");
        nickMarker = booleanSetting("nick_marker", source("Show nick marker"),
                source("Adds N to nicked players' names."), true);
        realNames = booleanSetting("real_names", source("Show real names on nametags"),
                source("Adds the real name found by Denick after the nicked name."), true);
        subsubcategory("enchantments", source("Enchantments"));
        enchantmentFormat = choiceSetting("enchantment_format", source("Enchantment format"),
                source("Controls how enchantment names and levels are shown."),
                PitEnchantmentFormat.BOLD_LEVELS, PitEnchantmentFormat.choices());
        showGem = booleanSetting("show_gem", source("Show gem"),
                source("Shows a green diamond on gemmed items."), true);
        showHeldItem = booleanSetting("show_held_item", source("Show held item enchantments"),
                source("Shows the held item's Pit enchantments."), true);
        showLeggings = booleanSetting("show_leggings", source("Show leggings enchantments"),
                source("Shows the leggings' Pit enchantments."), true);
        subsubcategory("timers", source("Timers"));
        pinTimer = booleanSetting("pin_timer", source("Show Pin timer"),
                source("Shows Pin timers after player names when enabled in Timers HUD."), true);
        venomTimer = booleanSetting("venom_timer", source("Show Venom timer"),
                source("Shows Venom timers after player names when enabled in Timers HUD."), true);
        jumpBoostTimer = booleanSetting("jump_boost_timer", source("Show Jump Boost timer"),
                source("Shows Jump Boost timers after player names when enabled in Timers HUD."),
                true);
        telebowTimer = booleanSetting("telebow_timer", source("Show Telebow cooldown"),
                source("Shows Telebow cooldowns after player names when enabled in Timers HUD."),
                true);
        subsubcategory("display", source("Display"));
        upwardOffset = integerSliderSetting("upward_offset", source("Upward offset (1/100 block)"),
                source("Moves enchantments above the nametag."), 36, 20, 40, 1);
    }

    public boolean nickMarker() {
        return nickMarker.get();
    }

    public boolean realNames() {
        return realNames.get();
    }

    public int timerTypes() {
        return (pinTimer.get() ? PlayerTimers.PIN : 0) | (venomTimer.get() ? PlayerTimers.VENOM : 0)
                | (jumpBoostTimer.get() ? PlayerTimers.JUMP_BOOST : 0)
                | (telebowTimer.get() ? PlayerTimers.TELEBOW : 0);
    }

    public int enchantmentFormat() {
        return enchantmentFormat.get();
    }

    public boolean showGem() {
        return showGem.get();
    }

    public boolean showHeldItem() {
        return showHeldItem.get();
    }

    public boolean showLeggings() {
        return showLeggings.get();
    }

    public int upwardOffset() {
        return upwardOffset.get();
    }
}
