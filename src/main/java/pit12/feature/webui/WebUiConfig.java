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

import static pit12.runtime.languages.Languages.source;

import java.util.Map;
import org.lwjgl.input.Keyboard;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ChoiceSetting.Choice;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.languages.Languages;

public final class WebUiConfig extends FeatureConfig {
    private final IntegerSetting keybind;
    private final ChoiceSetting language;

    public WebUiConfig(Languages languages) {
        super("webui", source("Settings"),
                new ConfigCategory("interface", source("Interface"), Integer.MAX_VALUE), "", false);
        Map<Integer, String> names = languages.names();
        Choice[] choices = new Choice[names.size()];
        int index = 0;
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            choices[index++] = new Choice(entry.getKey(), entry.getValue());
        }
        language = choiceSetting("language", source("Language"),
                source("Sets the language for the interface, commands, and HUD."), 0, choices);
        colorSetting("gui_color", source("Accent color"), source("Controls the interface accent."),
                0x7BADE2);
        booleanSetting("show_details", source("Show details"),
                source("Shows descriptions for features and settings."), false);
        keybind = keybindSetting("keybind", source("Open Web UI key"),
                source("Opens the web interface."), Keyboard.KEY_RSHIFT);
    }

    public IntegerSetting keybind() {
        return keybind;
    }

    public ChoiceSetting language() {
        return language;
    }
}
