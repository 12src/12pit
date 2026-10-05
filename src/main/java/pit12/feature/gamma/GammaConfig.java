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
package pit12.feature.gamma;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class GammaConfig extends FeatureConfig {
    private final IntegerSetting gamma;

    public GammaConfig() {
        super("gamma", source("Gamma"), new ConfigCategory("render", source("Render"), 100),
                source("Adjusts world brightness with a custom gamma value."));
        gamma = integerSliderSetting("gamma", source("Gamma value"),
                source("Controls brightness. Minecraft's normal range is 0 to 1."), 100, 0, 100, 1);
    }

    public float gamma() {
        return gamma.get().floatValue();
    }
}
