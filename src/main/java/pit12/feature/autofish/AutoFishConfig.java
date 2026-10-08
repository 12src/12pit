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
package pit12.feature.autofish;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class AutoFishConfig extends FeatureConfig {
    final BooleanSetting autoRecast;
    final IntegerSetting reelDelayMin;
    final IntegerSetting reelDelayMax;
    final IntegerSetting castDelayMin;
    final IntegerSetting castDelayMax;
    final IntegerSetting emptyHookChance;
    final IntegerSetting missHookChance;

    public AutoFishConfig() {
        super("autofish", source("Auto Fish"), new ConfigCategory("utility", source("Utility"), 75),
                source("Reels in when a fish bites and can cast again."));
        subsubcategory("general", source("General"));
        autoRecast = booleanSetting("auto_recast", source("Auto recast"),
                source("Casts once after Auto Fish reels in."), true);
        reelDelayMin = integerSliderSetting("reel_delay_min", source("Reel delay min"),
                source("Shortest wait before reeling in, in ticks."), 2, 0, 20, 1);
        reelDelayMax = integerSliderSetting("reel_delay_max", source("Reel delay max"),
                source("Longest wait before reeling in, in ticks."), 4, 0, 20, 1);
        castDelayMin = integerSliderSetting("cast_delay_min", source("Recast delay min"),
                source("Shortest wait before casting again, in ticks."), 15, 0, 20, 1);
        castDelayMax = integerSliderSetting("cast_delay_max", source("Recast delay max"),
                source("Longest wait before casting again, in ticks."), 20, 0, 20, 1);
        subsubcategory("hook_chances", source("Hook chances"));
        emptyHookChance = integerSliderSetting("empty_hook_chance", source("Empty hook chance"),
                source("Percent chance of waiting for the float to recover before reeling in."), 0,
                0, 100, 1);
        missHookChance = integerSliderSetting("miss_hook_chance", source("Miss hook chance"),
                source("Percent chance of ignoring a bite and waiting for the next one."), 0, 0,
                100, 1);
    }
}
