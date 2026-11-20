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
package pit12.feature.provider.telebow;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ChoiceSetting.Choice;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class TelebowPredictionConfig extends FeatureConfig {
    final IntegerSetting predictionMargin;
    final IntegerSetting teleportExtraMargin;
    final BooleanSetting correctCooldown;
    final ChoiceSetting cooldownDetection;

    public TelebowPredictionConfig() {
        super("telebow_prediction", source("Telebow Prediction"), ConfigCategory.PROVIDER,
                source("Tracks Telebow shots and cooldowns."), false);
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
    }
}
