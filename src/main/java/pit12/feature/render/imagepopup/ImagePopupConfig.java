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
package pit12.feature.render.imagepopup;

import static pit12.runtime.languages.Languages.source;

import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class ImagePopupConfig extends FeatureConfig {
    private final IntegerSetting intervalMinutes;
    private final IntegerSetting intervalSeconds;
    private final IntegerSetting durationSeconds;
    private final IntegerSetting maxWidthPercent;
    private final IntegerSetting maxHeightPercent;
    private final IntegerSetting fadeMillis;
    private final BooleanSetting dimBackground;
    private final BooleanSetting playSound;

    public ImagePopupConfig() {
        super("image_popup", source("Image Popup"), ConfigCategory.RENDER,
                source("Shows a random image from config/12pit/splash every few minutes."), true,
                false);
        intervalMinutes = integerSliderSetting("interval_minutes", source("Interval (minutes)"),
                source("Minutes between two images."), 30, 0, 1000000, 1);
        intervalSeconds = integerSliderSetting("interval_seconds", source("Interval (seconds)"),
                source("Seconds between two images, added to the minutes."), 0, 0, 59, 1);
        durationSeconds = integerSliderSetting("duration_seconds", source("Duration (seconds)"),
                source("How long an image stays on screen."), 15, 1, 600, 1);
        maxWidthPercent = integerSliderSetting("max_width_percent", source("Max width (%)"),
                source("Largest share of the screen width an image may use."), 60, 5, 100, 5);
        maxHeightPercent = integerSliderSetting("max_height_percent", source("Max height (%)"),
                source("Largest share of the screen height an image may use."), 60, 5, 100, 5);
        fadeMillis = integerSliderSetting("fade_millis", source("Fade (ms)"),
                source("Fade in and out time, 0 disables fading."), 400, 0, 2000, 50);
        dimBackground = booleanSetting("dim_background", source("Dim background"),
                source("Darkens the game while an image is shown."), true);
        playSound = booleanSetting("play_sound", source("Play sound"),
                source("Plays a short sound when an image appears."), false);
    }

    public IntegerSetting intervalMinutes() {
        return intervalMinutes;
    }

    public IntegerSetting intervalSeconds() {
        return intervalSeconds;
    }

    public IntegerSetting durationSeconds() {
        return durationSeconds;
    }

    public IntegerSetting maxWidthPercent() {
        return maxWidthPercent;
    }

    public IntegerSetting maxHeightPercent() {
        return maxHeightPercent;
    }

    public IntegerSetting fadeMillis() {
        return fadeMillis;
    }

    public BooleanSetting dimBackground() {
        return dimBackground;
    }

    public BooleanSetting playSound() {
        return playSound;
    }
}
