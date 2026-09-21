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
package pit12.feature.clickgui;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class ClickGuiConfig extends FeatureConfig {
    private final BooleanSetting dimBackground;
    private final BooleanSetting showTooltips;
    private final BooleanSetting animations;
    private final BooleanSetting scrollbars;
    private final IntegerSetting guiColor;
    private final IntegerSetting openKeybind;

    public ClickGuiConfig() {
        super("clickgui", "ClickGUI", new ConfigCategory("clickgui", "ClickGUI", Integer.MAX_VALUE),
                "Frames interface and input preferences.", false);
        dimBackground = booleanSetting("dim_background", "Dim background",
                "Darkens the game behind the ClickGUI.", true);
        showTooltips = booleanSetting("show_tooltips", "Show tooltips",
                "Displays descriptions while hovering controls.", true);
        animations = booleanSetting("animations", "Animations",
                "Animates hover and toggle transitions.", true);
        scrollbars = booleanSetting("scrollbars", "Scrollbars",
                "Displays scroll position beside overflowing frame content.", true);
        guiColor = colorSetting("gui_color", "GUI Theme",
                "Controls the accent color used throughout the ClickGUI.", 0x078D70);
        openKeybind = keybindSetting("keybind", "Open keybind",
                "Opens or closes the ClickGUI while no other screen is open.", Keyboard.KEY_RSHIFT);
    }

    public BooleanSetting dimBackground() {
        return dimBackground;
    }

    public BooleanSetting showTooltips() {
        return showTooltips;
    }

    public BooleanSetting animations() {
        return animations;
    }

    public BooleanSetting scrollbars() {
        return scrollbars;
    }

    public IntegerSetting guiColor() {
        return guiColor;
    }

    public IntegerSetting openKeybind() {
        return openKeybind;
    }
}
