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
package pit12.feature.webui;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;

public final class WebUiConfig extends FeatureConfig {
    private final IntegerSetting color;
    private final IntegerSetting keybind;

    public WebUiConfig() {
        super("webui", "Settings", new ConfigCategory("interface", "Interface", Integer.MAX_VALUE),
                "", false);
        color = colorSetting("gui_color", "Accent color", "Controls the interface accent.",
                0x7BADE2);
        booleanSetting("show_details", "Show details",
                "Shows descriptions for features and settings.", true);
        keybind = keybindSetting("keybind", "Open Web UI key", "Opens the web interface.",
                Keyboard.KEY_RSHIFT);
    }

    public IntegerSetting color() {
        return color;
    }

    public IntegerSetting keybind() {
        return keybind;
    }
}
