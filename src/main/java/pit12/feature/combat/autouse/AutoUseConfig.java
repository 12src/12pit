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
package pit12.feature.combat.autouse;

import static pit12.runtime.languages.Languages.source;

import java.util.EnumMap;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class AutoUseConfig extends FeatureConfig {
    enum UseItem {
        GOLDEN_HEAD,
        SOUP,
        RAGE_POTATO,
        FRACTURED_SOUL,
        AAA_RATED_STEAK,
        AURA_OF_PROTECTION,
        FIRST_AID_EGG
    }

    final BooleanSetting replaceWhenFull;
    final IntegerSetting replacementSlot;
    final BooleanSetting randomClickTiming;
    final ChoiceSetting inventoryDisplay;
    final IntegerSetting openDelay;
    final IntegerSetting swapDelay;
    final IntegerSetting closeDelay;
    final BooleanSetting resumeInputNextTick;
    final EnumMap<UseItem, ItemSettings> items = new EnumMap<>(UseItem.class);

    public AutoUseConfig() {
        super("autouse", source("Auto Use"), ConfigCategory.COMBAT,
                source("Automatically uses items when you are at low health."), true, false);
        category("inventory", source("Inventory"));
        replaceWhenFull = booleanSetting("replace_when_full",
                source("Replace when the hotbar is full"),
                source("Moves items from the inventory even when the hotbar is full."), true);
        replacementSlot = integerSliderSetting("replacement_slot", source("Slot to replace"),
                source("Uses this hotbar slot when there are no empty slots."), 9, 1, 9, 1);
        randomClickTiming = booleanSetting("random_click_timing", source("Random click timing"),
                source("Randomizes inventory click timing."), true);
        inventoryDisplay = choiceSetting("inventory_display", source("Inventory visibility"),
                source("Shows or hides the inventory when moving an item to the hotbar."), 0,
                new ChoiceSetting.Choice(0, source("Hidden")),
                new ChoiceSetting.Choice(1, source("Visible")));
        openDelay = integerSliderSetting("open_delay", source("Open delay (ticks)"),
                source("Minimum time between opening the inventory and clicking."), 2, 0, 10, 1);
        swapDelay = integerSliderSetting("swap_delay", source("Swap delay (ticks)"),
                source("Minimum time between inventory clicks."), 1, 0, 10, 1);
        closeDelay = integerSliderSetting("close_delay", source("Close delay (ticks)"),
                source("Time between clicking and closing the inventory."), 2, 0, 10, 1);
        resumeInputNextTick = booleanSetting("resume_input_next_tick",
                source("Resume input next tick"),
                source("Keeps input locked until the next tick after the inventory closes."), true);
        itemSettings(UseItem.GOLDEN_HEAD, "golden_head", "Golden Head", true, 20);
        itemSettings(UseItem.SOUP, "soup", "Soup", true, 1);
        itemSettings(UseItem.RAGE_POTATO, "rage_potato", "Rage Potato", true, 20);
        itemSettings(UseItem.FRACTURED_SOUL, "fractured_soul", "Fractured Soul", true, 20);
        itemSettings(UseItem.AAA_RATED_STEAK, "aaa_rated_steak", "AAA-Rated Steak", false, 200);
        itemSettings(UseItem.AURA_OF_PROTECTION, "aura_of_protection", "Aura of Protection", false,
                80);
        itemSettings(UseItem.FIRST_AID_EGG, "first_aid_egg", "First-Aid Egg", false, 20);
    }

    private void itemSettings(UseItem item, String id, String name, boolean defaultEnabled,
            int defaultInterval) {
        category(id, name, false);
        BooleanSetting enabled = booleanSetting(id + ".enabled", source("Enabled"),
                source("Uses this item automatically."), defaultEnabled);
        BooleanSetting fromInventory =
                booleanSetting(id + ".from_inventory", source("Use from inventory"),
                        source("Moves this item to the hotbar when needed."), false);
        IntegerSetting health = integerSliderSetting(id + ".health_hp", source("Health (HP)"),
                source("Uses this item at or below this health."), 10, 1, 40, 1);
        IntegerSetting itemInterval =
                integerSliderSetting(id + ".use_interval", source("Use interval (ticks)"),
                        source("Waits this long before trying this item again."), defaultInterval,
                        1, 400, 1);
        IntegerSetting itemSwitchDelay = integerSliderSetting(id + ".switch_delay",
                source("Use delay (ticks)"),
                source("Waits this long to right click after switching to an item."), 1, 0, 20, 1);
        IntegerSetting itemReturnDelay =
                integerSliderSetting(id + ".return_delay", source("Switch back delay (ticks)"),
                        source("Waits this long to switch back after use."), 1, 1, 20, 1);
        items.put(item, new ItemSettings(enabled, fromInventory, health, itemInterval,
                itemSwitchDelay, itemReturnDelay));
    }

    static final class ItemSettings {
        final BooleanSetting enabled;
        final BooleanSetting fromInventory;
        final IntegerSetting health;
        final IntegerSetting interval;
        final IntegerSetting switchDelay;
        final IntegerSetting returnDelay;

        ItemSettings(BooleanSetting enabled, BooleanSetting fromInventory, IntegerSetting health,
                IntegerSetting interval, IntegerSetting switchDelay, IntegerSetting returnDelay) {
            this.enabled = enabled;
            this.fromInventory = fromInventory;
            this.health = health;
            this.interval = interval;
            this.switchDelay = switchDelay;
            this.returnDelay = returnDelay;
        }

        boolean ready(float healthPoints) {
            return enabled.get() && healthPoints <= health.get();
        }
    }
}
