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
package pit12.shared.input;

import org.lwjgl.input.Keyboard;

public final class Keybind {
    private Keybind() {}

    // Keep old single-key values. The next byte holds an optional modifier key.
    public static int encode(int key, int modifier) {
        return key | modifier << 8;
    }

    public static int key(int binding) {
        return binding & 0xFF;
    }

    public static int modifier(int binding) {
        return binding >>> 8;
    }

    public static boolean isModifier(int key) {
        return key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT
                || key == Keyboard.KEY_LCONTROL || key == Keyboard.KEY_RCONTROL
                || key == Keyboard.KEY_LMENU || key == Keyboard.KEY_RMENU
                || key == Keyboard.KEY_LMETA || key == Keyboard.KEY_RMETA;
    }

    public static boolean valid(int binding) {
        if (binding == Keyboard.KEY_NONE)
            return true;
        int key = key(binding);
        int modifier = modifier(binding);
        return key != Keyboard.KEY_NONE && Keyboard.getKeyName(key) != null
                && (modifier == 0 || isModifier(modifier) && modifier != key);
    }

    public static int parse(String name) {
        String[] keys = name.split("\\+", -1);
        if (keys.length > 2)
            throw new IllegalArgumentException("Expected a key with at most one modifier");
        int key = Keyboard.getKeyIndex(keys[keys.length - 1]);
        if (key == Keyboard.KEY_NONE && !"NONE".equals(name))
            throw new IllegalArgumentException("Unknown key");
        int modifier = keys.length == 2 ? Keyboard.getKeyIndex(keys[0]) : 0;
        if (keys.length == 2 && !isModifier(modifier))
            throw new IllegalArgumentException("Expected a modifier key");
        return encode(key, modifier);
    }

    public static String name(int binding) {
        int modifier = modifier(binding);
        return (modifier == 0 ? "" : Keyboard.getKeyName(modifier) + "+")
                + Keyboard.getKeyName(key(binding));
    }

    public static boolean matches(int binding, int eventKey) {
        int modifier = modifier(binding);
        return eventKey != Keyboard.KEY_NONE && key(binding) == eventKey
                && (modifier == 0 || Keyboard.isCreated() && Keyboard.isKeyDown(modifier));
    }

    public static boolean held(int binding) {
        int key = key(binding);
        return key != Keyboard.KEY_NONE && Keyboard.isCreated() && Keyboard.isKeyDown(key)
                && matches(binding, key);
    }
}
