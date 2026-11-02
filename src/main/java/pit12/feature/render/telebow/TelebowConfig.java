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
package pit12.feature.render.telebow;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ChoiceSetting.Choice;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.timers.TelebowSettings;

public final class TelebowConfig extends FeatureConfig implements TelebowSettings {
    public final BooleanSetting own;
    public final BooleanSetting friends;
    public final BooleanSetting enemies;
    public final BooleanSetting others;
    public final IntegerSetting predictionMargin;
    public final IntegerSetting teleportExtraMargin;
    public final BooleanSetting ignoreCooldown;
    public final BooleanSetting correctCooldown;
    public final ChoiceSetting cooldownDetection;
    public final BooleanSetting aimPoints;
    public final BooleanSetting aimHoldingOnly;
    public final BooleanSetting aimFriends;
    public final BooleanSetting aimEnemies;
    public final BooleanSetting aimOthers;
    public final BooleanSetting aimNames;
    public final DoubleSetting aimTolerance;
    public final ColorSetting ownColor;
    public final ColorSetting friendColor;
    public final ColorSetting enemyColor;
    public final ColorSetting otherColor;
    public final BooleanSetting showNames;
    public final IntegerSetting holdTime;
    public final IntegerSetting fadeTime;

    public TelebowConfig() {
        super("telebow", source("Telebow Prediction"), ConfigCategory.RENDER, source(
                "Shows Telebow trails, predicted landing points, and shooter names through blocks."));
        subcategory("shooters", source("Shooters"));
        own = booleanSetting("own", source("Own"), source("Shows your trails and landing points."),
                true);
        friends = booleanSetting("friends", source("Friends"),
                source("Shows friends' trails and landing points."), true);
        enemies = booleanSetting("enemies", source("Enemies"),
                source("Shows enemies' trails and landing points."), true);
        others = booleanSetting("others", source("Others"),
                source("Shows other players' trails and landing points."), true);
        subcategory("prediction", source("Prediction"));
        ignoreCooldown = booleanSetting("ignore_cooldown", source("Ignore arrows during cooldown"),
                source("Skips predictions for arrows fired during a tracked Telebow cooldown."),
                true);
        correctCooldown = booleanSetting("correct_cooldown", source("Correct cooldown estimates"),
                source("Updates cooldown estimates after successful Telebow teleports."), true);
        cooldownDetection = choiceSetting("cooldown_detection", source("Cooldown detection"),
                source("Shows cooldowns immediately after shooting or after a successful teleport."),
                1, new Choice(0, source("On shot")), new Choice(1, source("On teleport")));
        predictionMargin =
                integerSliderSetting("prediction_margin_ms", source("Prediction margin (ms)"),
                        source("Allows for delays when predicting shots and tracking cooldowns."),
                        250, 0, 1000, 25);
        teleportExtraMargin = integerSliderSetting("teleport_extra_margin_ms",
                source("Extra teleport margin (ms)"),
                source("Adds to the prediction margin for Telebow teleports."), 100, 0, 1000, 25);
        subcategory("aim", source("Aim points"));
        aimPoints = booleanSetting("aim_points", source("Show aim points"), source(
                "Shows where to aim to land near another player's predicted Telebow landing point. Use a fully drawn shot while sneaking."),
                false);
        aimHoldingOnly = booleanSetting("aim_holding_only", source("Only while holding Telebow"),
                source("Shows aim points only while holding a Telebow."), true);
        aimFriends = booleanSetting("aim_friends", source("Friends"),
                source("Shows aim points for landing near friends' Telebow landing points."), true);
        aimEnemies = booleanSetting("aim_enemies", source("Enemies"),
                source("Shows aim points for landing near enemies' Telebow landing points."), true);
        aimOthers = booleanSetting("aim_others", source("Others"),
                source("Shows aim points for landing near other players' Telebow landing points."),
                true);
        aimNames = booleanSetting("aim_names", source("Show names"),
                source("Shows player names next to aim points."), true);
        aimTolerance = doubleSliderSetting("aim_tolerance_blocks", source("Tolerance (blocks)"),
                source("Maximum distance between your predicted landing point and the other player's landing point."),
                2, 0.25, 8, 0.25);
        subcategory("appearance", source("Appearance"));
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

    @Override
    public boolean correctCooldown() {
        return correctCooldown.get();
    }

    @Override
    public int cooldownDetection() {
        return cooldownDetection.get();
    }

    @Override
    public int predictionMargin() {
        return predictionMargin.get();
    }

    @Override
    public int teleportExtraMargin() {
        return teleportExtraMargin.get();
    }
}
