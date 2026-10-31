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
package pit12.feature.render.freecam;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.KeybindSetting;

public final class FreeCameraConfig extends FeatureConfig {
    private final DoubleSetting speed;
    private final KeybindSetting toggleKey;

    public FreeCameraConfig() {
        super("freecam", source("Free Camera"), ConfigCategory.RENDER, source(
                "Move the camera with the movement keys, jump and sneak. Hold sprint to move faster."),
                true, false);
        speed = doubleSliderSetting("speed", source("Camera speed"),
                source("Blocks the camera moves per tick."), 0.5, 0.1, 2.0, 0.05);
        toggleKey = keybindSetting("toggle_key", source("Toggle key"),
                source("Toggles the free camera."), Keyboard.KEY_NONE);
    }

    public DoubleSetting speed() {
        return speed;
    }

    public KeybindSetting toggleKey() {
        return toggleKey;
    }
}
