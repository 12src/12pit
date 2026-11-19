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
package pit12.feature.misc.autodenick;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;

public final class AutoDenickConfig extends FeatureConfig {
    final BooleanSetting successOnly;
    final BooleanSetting monitor;

    public AutoDenickConfig() {
        super("auto_denick", source("Auto Denick"), ConfigCategory.MISC,
                source("Looks up nicked players when you enter the Pit or they join."));
        successOnly = booleanSetting("success_only", source("Only show successful queries"),
                source("Hides failed automatic queries and monitoring messages."), true);
        monitor = booleanSetting("monitor", source("Keep monitoring"),
                source("Continues automatic lookups until a real name is found."), true);
    }
}
