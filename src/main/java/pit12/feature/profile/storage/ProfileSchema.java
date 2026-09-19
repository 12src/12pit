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
package pit12.feature.profile.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import pit12.runtime.config.BooleanSetting;
import pit12.runtime.config.ColorSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.IntegerSetting;
import pit12.runtime.config.KeybindSetting;
import pit12.runtime.config.Setting;

public final class ProfileSchema {
    public enum ValueType {
        BOOLEAN, INTEGER, UNSUPPORTED
    }

    private final Map<String, Map<String, ValueType>> features;

    private ProfileSchema(Map<String, Map<String, ValueType>> features) {
        this.features = features;
    }

    public static ProfileSchema capture(ConfigCatalog catalog) {
        LinkedHashMap<String, Map<String, ValueType>> features =
                new LinkedHashMap<String, Map<String, ValueType>>();
        for (FeatureConfig feature : catalog.features()) {
            LinkedHashMap<String, ValueType> settings = new LinkedHashMap<String, ValueType>();
            for (Setting<?> setting : feature.settings()) {
                ValueType type;
                if (setting instanceof BooleanSetting) {
                    type = ValueType.BOOLEAN;
                } else if (setting instanceof IntegerSetting || setting instanceof KeybindSetting
                        || setting instanceof ColorSetting) {
                    type = ValueType.INTEGER;
                } else {
                    type = ValueType.UNSUPPORTED;
                }
                settings.put(setting.id(), type);
            }
            features.put(feature.id(), Collections.unmodifiableMap(settings));
        }
        return new ProfileSchema(Collections.unmodifiableMap(features));
    }

    public Map<String, Map<String, ValueType>> features() {
        return features;
    }
}
