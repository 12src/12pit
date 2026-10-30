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
package pit12.feature.chatmacro;

import static pit12.runtime.languages.Languages.source;

import org.lwjgl.input.Keyboard;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ConfigCategory;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.KeybindSetting;

public final class ChatMacroConfig extends FeatureConfig {
    final BooleanSetting blockInputs;
    final KeybindSetting spawnKey;

    public ChatMacroConfig() {
        super("chatmacro", source("Chat Macro"), ConfigCategory.MISC,
                source("Sends chat messages with key bindings."), false);
        blockInputs = booleanSetting("block_inputs", source("Block inputs"), source(
                "Blocks movement, clicks, and key presses for one tick before sending a macro."),
                true);
        spawnKey = keybindSetting("spawn_key", source("Spawn macro key"), source(
                "Sends /spawn once you are out of combat. Press again to cancel while waiting."),
                Keyboard.KEY_NONE);
    }
}
