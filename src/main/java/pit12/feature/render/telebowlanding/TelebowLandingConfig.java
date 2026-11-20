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
package pit12.feature.render.telebowlanding;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class TelebowLandingConfig extends FeatureConfig {
    public final BooleanSetting own;
    public final BooleanSetting friends;
    public final BooleanSetting enemies;
    public final BooleanSetting others;
    public final BooleanSetting ignoreCooldown;
    public final ColorSetting ownColor;
    public final ColorSetting friendColor;
    public final ColorSetting enemyColor;
    public final ColorSetting otherColor;
    public final BooleanSetting showNames;
    public final IntegerSetting holdTime;
    public final IntegerSetting fadeTime;

    public TelebowLandingConfig() {
        super("telebow", source("Telebow Landing"), ConfigCategory.RENDER,
                source("Shows where Telebow shots are likely to land."));
        category("shooters", source("Shooters"));
        own = booleanSetting("own", source("Own"), source("Shows your trails and landing points."),
                true);
        friends = booleanSetting("friends", source("Friends"),
                source("Shows friends' trails and landing points."), true);
        enemies = booleanSetting("enemies", source("Enemies"),
                source("Shows enemies' trails and landing points."), true);
        others = booleanSetting("others", source("Others"),
                source("Shows other players' trails and landing points."), true);
        category("prediction", source("Prediction"));
        ignoreCooldown = booleanSetting("ignore_cooldown", source("Ignore arrows during cooldown"),
                source("Skips predictions for arrows fired during a tracked Telebow cooldown."),
                true);
        category("appearance", source("Appearance"));
        ownColor = colorPickerSetting("own_color", source("Own color"),
                source("Color and opacity of your Telebow predictions."), 0xFF55FFFF);
        friendColor = colorPickerSetting("friend_color", source("Friend color"),
                source("Color and opacity of friends' Telebow predictions."), 0xFF55FF55);
        enemyColor = colorPickerSetting("enemy_color", source("Enemy color"),
                source("Color and opacity of enemies' Telebow predictions."), 0xFFFF5555);
        otherColor = colorPickerSetting("other_color", source("Other player color"),
                source("Color and opacity of other players' Telebow predictions."), 0xFFFFFFFF);
        holdTime = integerSliderSetting("hold_time_ms", source("Hold time (ms)"),
                source("Time to keep predictions visible after the arrow lands or disappears."),
                2000, 0, 5000, 100);
        fadeTime = integerSliderSetting("fade_time_ms", source("Fade time (ms)"),
                source("Time for predictions to fade after the hold time."), 1000, 100, 3000, 100);
        showNames = booleanSetting("show_names", source("Show names"),
                source("Shows the shooter's display name at the predicted landing point."), true);
    }
}
