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
package pit12.feature.quickmath;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;

public final class AutoQuickMathConfig extends FeatureConfig {
    private final DoubleSetting delay;

    public AutoQuickMathConfig() {
        super("auto_quick_math", source("Auto Quick Math"),
                new ConfigCategory("utility", source("Utility"), 75),
                source("Solves the QUICK MATHS! expression and answers in chat automatically."),
                true, false);
        delay = doubleSliderSetting("delay", source("Answer delay"),
                source("Waits this many seconds before sending the answer."), 1.0, 0.0, 5.0, 0.1);
    }

    public double delay() {
        return delay.get();
    }
}
