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

import java.util.Objects;

public final class ConfigOption<T> {
    public enum Kind {
        BOOLEAN, NUMBER, COLOR, KEYBIND
    }

    private final Setting<T> setting;
    private final Kind kind;

    ConfigOption(Setting<T> setting, Kind kind) {
        this.setting = Objects.requireNonNull(setting, "setting");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (!isCompatible(setting, kind)) {
            throw new IllegalArgumentException(
                    "Setting " + setting.id() + " is incompatible with option kind " + kind);
        }
    }

    public Setting<T> setting() {
        return setting;
    }

    public Kind kind() {
        return kind;
    }

    private static boolean isCompatible(Setting<?> setting, Kind kind) {
        switch (kind) {
            case BOOLEAN:
                return setting instanceof BooleanSetting;
            case NUMBER:
                return setting instanceof NumberSetting<?> && ((NumberSetting<?>) setting)
                        .minimumValue() < ((NumberSetting<?>) setting).maximumValue();
            case COLOR:
            case KEYBIND:
                return setting instanceof IntegerSetting;
            default:
                return false;
        }
    }
}
