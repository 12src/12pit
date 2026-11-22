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
package pit12.feature.render.cosmetic;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class CosmeticConfig extends FeatureConfig {
    public static final int MYSELF = 0;
    public static final int OTHERS = 1;
    public static final int EVERYONE = 2;
    private static final String[] STYLES = {"plain", "tabby", "spots"};
    private final ChoiceSetting style;
    private final IntegerSetting size;
    private final ChoiceSetting target;
    private final IntegerSetting distance;

    public CosmeticConfig() {
        super("cat_ears", source("Cat Ears"), ConfigCategory.RENDER,
                source("Draws cat ears on player models. Only you can see them."));
        section("appearance", source("Appearance"));
        style = choiceSetting("style", source("Style"), source("Pattern of the cat ears."), 0,
                new ChoiceSetting.Choice(0, source("Plain")),
                new ChoiceSetting.Choice(1, source("Tabby")),
                new ChoiceSetting.Choice(2, source("Spots")));
        size = integerSliderSetting("size", source("Size (%)"),
                source("Scales the ears from the top of the head."), 100, 50, 200, 5);
        section("visibility", source("Visibility"));
        target = choiceSetting("target", source("Show on"),
                source("Chooses whose heads get the ears."), EVERYONE,
                new ChoiceSetting.Choice(MYSELF, source("Myself")),
                new ChoiceSetting.Choice(OTHERS, source("Other players")),
                new ChoiceSetting.Choice(EVERYONE, source("Everyone")));
        distance = integerSliderSetting("distance", source("Render distance"),
                source("Stops drawing ears beyond this distance, in blocks."), 64, 8, 128, 8);
    }

    public String style() {
        return STYLES[style.get()];
    }

    public int size() {
        return size.get();
    }

    public int target() {
        return target.get();
    }

    public int distance() {
        return distance.get();
    }
}
