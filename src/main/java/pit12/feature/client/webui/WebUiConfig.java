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
package pit12.feature.client.webui;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.KeybindSetting;
import pit12.runtime.config.StringSetting;

public final class WebUiConfig extends FeatureConfig {
    private final KeybindSetting keybind;
    private final BooleanSetting languageHint;
    private final StringSetting language;
    private final BooleanSetting discordRpc;

    public WebUiConfig() {
        super("webui", source("Settings"), null,
                source("Configures language, interface appearance, key bindings, and Discord RPC."),
                false);
        language = stringSetting("language", source("Language"),
                source("Sets the language for the interface, commands, and HUD."), "en-us");
        colorSetting("gui_color", source("Accent color"),
                source("Sets the interface accent color."), 0x7BADE2);
        discordRpc = booleanSetting("discord_rpc", "Discord RPC",
                source("Shows 12pit activity and elapsed time on your Discord profile."), true);
        keybind = keybindSetting("keybind", source("Interface key"),
                source("Opens the HUD editor. Press again with no HUD selected to open settings."),
                Keyboard.KEY_RSHIFT);
        languageHint = booleanSetting("language_hint", true);
    }

    public KeybindSetting keybind() {
        return keybind;
    }

    public BooleanSetting languageHint() {
        return languageHint;
    }

    public StringSetting language() {
        return language;
    }

    public BooleanSetting discordRpc() {
        return discordRpc;
    }
}
