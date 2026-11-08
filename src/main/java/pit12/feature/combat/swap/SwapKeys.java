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
package pit12.feature.combat.swap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.input.Keyboard;

final class SwapKeys {
    // Minecraft stores mouse buttons as button - 100.
    private static final String[] NAMES = new String[100 + Keyboard.KEYBOARD_SIZE];
    private static final String[] OVERLAY_NAMES = new String[NAMES.length];
    private static final Map<String, Integer> CODES = new HashMap<>();
    private static final List<String> SUGGESTIONS = new ArrayList<>(Arrays.asList("A", "B", "C",
            "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
            "U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "F1",
            "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14",
            "F15", "F16", "F17", "F18", "F19", "NUMPAD0", "NUMPAD1", "NUMPAD2", "NUMPAD3",
            "NUMPAD4", "NUMPAD5", "NUMPAD6", "NUMPAD7", "NUMPAD8", "NUMPAD9", "ADD", "SUBTRACT",
            "MULTIPLY", "DIVIDE", "DECIMAL", "NUMPADENTER", "SPACE", "TAB", "RETURN", "BACK",
            "CAPITAL", "ESCAPE", "LSHIFT", "RSHIFT", "LCONTROL", "RCONTROL", "LMENU", "RMENU",
            "MINUS", "EQUALS", "COMMA", "PERIOD", "SLASH", "GRAVE", "UP", "DOWN", "LEFT", "RIGHT"));
    static {
        for (String name : SUGGESTIONS) {
            int code = Keyboard.getKeyIndex(name);
            String label = name.startsWith("NUMPAD") ? "P" + name.substring(6) : name;
            switch (code) {
                case Keyboard.KEY_ADD:
                    label = "P+";
                    break;
                case Keyboard.KEY_SUBTRACT:
                    label = "P-";
                    break;
                case Keyboard.KEY_MULTIPLY:
                    label = "P*";
                    break;
                case Keyboard.KEY_DIVIDE:
                    label = "P/";
                    break;
                case Keyboard.KEY_DECIMAL:
                    label = "P.";
                    break;
            }
            NAMES[code + 100] = name;
            OVERLAY_NAMES[code + 100] = label;
            CODES.put(name, code);
        }
        for (int button = 0; button < 8; button++) {
            String name = "MOUSE" + (button + 1);
            NAMES[button] = name;
            OVERLAY_NAMES[button] = "M" + (button + 1);
            CODES.put(name, button - 100);
            SUGGESTIONS.add(name);
        }
    }

    private SwapKeys() {}

    static String name(int key) {
        return NAMES[key + 100];
    }

    static String overlayName(int key) {
        return OVERLAY_NAMES[key + 100];
    }

    static boolean valid(int key) {
        return key >= -100 && key < Keyboard.KEYBOARD_SIZE && NAMES[key + 100] != null;
    }

    static int parse(String name) {
        Integer code = CODES.get(name.toUpperCase(Locale.ROOT));
        return code == null ? Keyboard.KEY_NONE : code;
    }

    static List<String> suggestions() {
        return SUGGESTIONS;
    }
}
