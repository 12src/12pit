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
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.Setting;

public final class ProfileSchema {
    private final Map<String, Map<String, Setting.StorageType>> features;

    private ProfileSchema(Map<String, Map<String, Setting.StorageType>> features) {
        this.features = features;
    }

    public static ProfileSchema capture(ConfigCatalog catalog) {
        LinkedHashMap<String, Map<String, Setting.StorageType>> features =
                new LinkedHashMap<String, Map<String, Setting.StorageType>>();
        for (FeatureConfig feature : catalog.features()) {
            LinkedHashMap<String, Setting.StorageType> settings =
                    new LinkedHashMap<String, Setting.StorageType>();
            for (Setting<?> setting : feature.settings()) {
                settings.put(setting.id(), setting.storageType());
            }
            features.put(feature.id(), Collections.unmodifiableMap(settings));
        }
        return new ProfileSchema(Collections.unmodifiableMap(features));
    }

    public Map<String, Map<String, Setting.StorageType>> features() {
        return features;
    }
}
