/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 12dev <https://github.com/12src>
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
package pit12.runtime.config;

public final class KeybindSetting extends Setting<Integer> {
    KeybindSetting(String id, String displayName, String description, int defaultValue) {
        super(id, displayName, description, Integer.valueOf(defaultValue));
    }

    @Override
    protected Integer requireValue(Object candidate) {
        if (!(candidate instanceof Number)) {
            throw new IllegalArgumentException("Setting " + id() + " requires an integer key code");
        }
        int keyCode = ((Number) candidate).intValue();
        if (keyCode < 0 || keyCode > 255) {
            throw new IllegalArgumentException(
                    "Setting " + id() + " requires a key code from 0 to 255");
        }
        return Integer.valueOf(keyCode);
    }
}
