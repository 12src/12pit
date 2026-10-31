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
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.DoubleSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.KeybindSetting;

public final class FreeCameraConfig extends FeatureConfig {
    private final DoubleSetting speed;
    private final IntegerSetting maxDistance;
    private final BooleanSetting blockPlayerInputs;
    private final BooleanSetting blockPlayerMovement;
    private final BooleanSetting hideHand;
    private final KeybindSetting toggleKey;

    public FreeCameraConfig() {
        super("freecam", source("Free Camera"), ConfigCategory.RENDER, source(
                "Detaches the camera so you can look around while your player stays in place."),
                true, false);
        speed = doubleSliderSetting("speed", source("Camera speed"),
                source("Blocks the camera moves per tick."), 0.5, 0.1, 2.0, 0.05);
        maxDistance = integerSliderSetting("max_distance", source("Max distance"),
                source("Maximum distance from the player, in blocks."), 64, 8, 256, 4);
        blockPlayerInputs = booleanSetting("block_player_inputs", source("Freeze player actions"),
                source("Blocks attacks and item use while the camera is detached."), true);
        blockPlayerMovement = booleanSetting("block_player_movement",
                source("Freeze player movement"),
                source("Stops movement input from moving the player while the camera is detached."),
                true);
        hideHand = booleanSetting("hide_hand", source("Hide hand"),
                source("Hides your own hand and held item while the camera is detached."), true);
        toggleKey = keybindSetting("toggle_key", source("Toggle key"),
                source("Toggles the free camera."), Keyboard.KEY_NONE);
    }

    public DoubleSetting speed() {
        return speed;
    }

    public IntegerSetting maxDistance() {
        return maxDistance;
    }

    public BooleanSetting blockPlayerInputs() {
        return blockPlayerInputs;
    }

    public BooleanSetting blockPlayerMovement() {
        return blockPlayerMovement;
    }

    public BooleanSetting hideHand() {
        return hideHand;
    }

    public KeybindSetting toggleKey() {
        return toggleKey;
    }
}
