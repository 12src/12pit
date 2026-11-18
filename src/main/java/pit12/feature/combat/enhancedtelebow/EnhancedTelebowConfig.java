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
package pit12.feature.combat.enhancedtelebow;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;

public final class EnhancedTelebowConfig extends FeatureConfig {
    final BooleanSetting keepAim;
    final BooleanSetting sneakOnRelease;

    public EnhancedTelebowConfig() {
        super("enhanced_telebow", source("Enhanced Telebow"), ConfigCategory.COMBAT,
                source("Some enhancements to the Telebow."), true, false);
        keepAim = booleanSetting("keep_aim", source("Keep aim on teleport"),
                source("Keeps your aim when you teleport."), true);
        sneakOnRelease = booleanSetting("sneak_on_release", source("Sneak on release"),
                source("Sneaks before releasing the Telebow. Left click to release bypass this."),
                false);
    }
}
