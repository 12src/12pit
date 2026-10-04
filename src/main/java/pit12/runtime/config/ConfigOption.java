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
package pit12.runtime.config;

public final class ConfigOption<T> {
    public enum Kind {
        BOOLEAN, NUMBER, CHOICE, COLOR, COLOR_PICKER, KEYBIND
    }

    private final Setting<T> setting;
    private final Kind kind;
    private final ConfigGroup subcategory;
    private final ConfigGroup subsubcategory;

    ConfigOption(Setting<T> setting, Kind kind, ConfigGroup subcategory,
            ConfigGroup subsubcategory) {
        this.setting = setting;
        this.kind = kind;
        this.subcategory = subcategory;
        this.subsubcategory = subsubcategory;
    }

    public Setting<T> setting() {
        return setting;
    }

    public Kind kind() {
        return kind;
    }

    public ConfigGroup subcategory() {
        return subcategory;
    }

    public ConfigGroup subsubcategory() {
        return subsubcategory;
    }
}
