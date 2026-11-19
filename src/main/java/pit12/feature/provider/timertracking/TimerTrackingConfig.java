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
package pit12.feature.provider.timertracking;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class TimerTrackingConfig extends FeatureConfig {
    final IntegerSetting pinHitWindow;
    final IntegerSetting venomHitWindow;
    final DoubleSetting venomReach;
    final DoubleSetting venomHitboxMargin;
    final BooleanSetting jumpBoostSoundRequired;
    final BooleanSetting jumpBoostResetOnLowJump;
    final IntegerSetting jumpBoostGrace;

    public TimerTrackingConfig() {
        super("timer_tracking", source("Timer Tracking"), ConfigCategory.PROVIDER,
                source("Controls how Pin, Venom, and Jump Boost timers are estimated."), false);
        category("pin", "Pin");
        pinHitWindow = integerSliderSetting("pin_hit_window_ms", source("Prediction margin (ms)"),
                source("Time allowed for delayed damage to count as a Pin hit."), 250, 25, 1000,
                25);
        category("venom", "Venom");
        venomHitWindow =
                integerSliderSetting("venom_hit_window_ms", source("Prediction margin (ms)"),
                        source("Time allowed for delayed damage to count as a Venom hit."), 250, 25,
                        1500, 25);
        venomReach = doubleSliderSetting("venom_reach_blocks", source("Maximum reach (blocks)"),
                source("Maximum distance at which hits count toward Venom estimates."), 4.5, 2, 6,
                0.1);
        venomHitboxMargin = doubleSliderSetting("venom_hitbox_margin_blocks",
                source("Hitbox margin (blocks)"),
                source("Counts hits up to this far outside player hitboxes toward Venom timers."),
                0.4, 0, 1, 0.05);
        category("jump_boost", "Jump Boost");
        jumpBoostSoundRequired =
                booleanSetting("jump_boost_sound_required", source("Sound required"),
                        source("Requires a drinking sound to start a Jump Boost timer."), false);
        jumpBoostResetOnLowJump = booleanSetting("jump_boost_reset_on_low_jump",
                source("Reset on low jump"),
                source("Clears the timer when an unobstructed jump is too low for Jump Boost IV."),
                true);
        jumpBoostGrace = integerSliderSetting("jump_boost_grace_ms",
                source("Prediction margin (ms)"),
                source("Time after a Jump Boost timer starts before low jumps can clear it."), 250,
                0, 1000, 25);
    }
}
