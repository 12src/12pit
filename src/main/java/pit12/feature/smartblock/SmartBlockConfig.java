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
package pit12.feature.smartblock;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class SmartBlockConfig extends FeatureConfig {
    final BooleanSetting bulletTime;
    final BooleanSetting bruiser;
    final ChoiceSetting priority;
    final BooleanSetting bulletTimeNearArrows;
    final IntegerSetting arrowRange;
    final BooleanSetting emptyHand;
    final IntegerSetting switchDelay;
    final IntegerSetting restoreDelay;
    final BooleanSetting lockSlot;

    public SmartBlockConfig() {
        super("smart_block", source("Smart Block"),
                new ConfigCategory("player", source("Player"), 50),
                source("Selects a Bullet Time or Bruiser sword from the hotbar while blocking."),
                true, false);
        subsubcategory("enchantments", source("Enchantments"));
        bulletTime = booleanSetting("bullet_time", source("Use Bullet Time"),
                source("Selects swords with Bullet Time."), true);
        bruiser = booleanSetting("bruiser", source("Use Bruiser"),
                source("Selects swords with Bruiser."), true);
        priority = choiceSetting("priority", source("Enchantment priority"), source(
                "Chooses the highest level of this enchantment. Ties use the other enabled enchantment's level."),
                0, new ChoiceSetting.Choice(0, source("Bullet Time first")),
                new ChoiceSetting.Choice(1, source("Bruiser first")));
        bulletTimeNearArrows = booleanSetting("bullet_time_near_arrows",
                source("Prefer Bullet Time near arrows"),
                source("Switches to a Bullet Time sword when a flying arrow is within the detection range."),
                false);
        arrowRange = integerSliderSetting("arrow_range", source("Arrow detection range"),
                source("Detects flying arrows within this distance, in blocks."), 12, 1, 32, 1);
        subsubcategory("switching", source("Switching"));
        emptyHand = booleanSetting("empty_hand", source("Trigger with fist"),
                source("Also starts Smart Block when holding use with an empty hand."), false);
        lockSlot = booleanSetting("lock_slot", source("Prevent manual slot changes"), source(
                "Blocks hotbar keys, scrolling and pick block during Smart Block. Otherwise, changing slots ends the current switch."),
                true);
        switchDelay = integerSliderSetting("switch_delay", source("Switch delay"),
                source("Waits this many ticks before selecting the blocking sword."), 1, 0, 20, 1);
        restoreDelay = integerSliderSetting("restore_delay", source("Restore delay"), source(
                "Waits this many ticks after releasing use before restoring the original slot."), 0,
                0, 20, 1);
    }
}
