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

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class AutoUseConfig extends FeatureConfig {
    final IntegerSetting interval;
    final IntegerSetting switchDelay;
    final IntegerSetting returnDelay;
    final ItemSettings goldenHead;
    final ItemSettings soup;
    final ItemSettings ragePotato;
    final ItemSettings fracturedSoul;

    public AutoUseConfig() {
        super("autouse", source("Auto Use"), ConfigCategory.COMBAT,
                source("Automatically uses items when you are at low health."), true, false);
        section("general", source("General"));
        interval = integerSliderSetting("use_interval", source("Use interval (ticks)"),
                source("Minimum time between use attempts."), 20, 4, 100, 1);
        switchDelay = integerSliderSetting("switch_delay", source("Use delay (ticks)"),
                source("Waits this long to right click after switching to an item."), 1, 0, 20, 1);
        returnDelay = integerSliderSetting("return_delay", source("Switch back delay (ticks)"),
                source("Waits this long to switch back after use."), 1, 1, 20, 1);
        goldenHead = itemSettings("golden_head", source("Golden Head"));
        soup = itemSettings("soup", source("Soup"));
        ragePotato = itemSettings("rage_potato", source("Rage Potato"));
        fracturedSoul = itemSettings("fractured_soul", source("Fractured Soul"));
    }

    private ItemSettings itemSettings(String id, String name) {
        section(id, name);
        BooleanSetting enabled = booleanSetting(id + ".enabled", source("Enabled"),
                source("Uses this item automatically."), true);
        IntegerSetting health = integerSliderSetting(id + ".health_hp", source("Health (HP)"),
                source("Uses this item at or below this health."), 10, 1, 40, 1);
        return new ItemSettings(enabled, health);
    }

    static final class ItemSettings {
        final BooleanSetting enabled;
        final IntegerSetting health;

        ItemSettings(BooleanSetting enabled, IntegerSetting health) {
            this.enabled = enabled;
            this.health = health;
        }

        boolean ready(float healthPoints) {
            return enabled.get() && healthPoints <= health.get();
        }
    }
}
