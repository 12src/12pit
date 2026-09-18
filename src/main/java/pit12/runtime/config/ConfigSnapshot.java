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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConfigSnapshot {
    private final Map<String, Map<String, Object>> features;

    public ConfigSnapshot(Map<String, ? extends Map<String, ?>> features) {
        if (features == null) {
            throw new NullPointerException("features");
        }
        LinkedHashMap<String, Map<String, Object>> featureCopy =
                new LinkedHashMap<String, Map<String, Object>>();
        for (Map.Entry<String, ? extends Map<String, ?>> featureEntry : features.entrySet()) {
            String featureId = ConfigNames.requireStableId(featureEntry.getKey(), "feature id");
            Map<String, ?> sourceSettings = featureEntry.getValue();
            if (sourceSettings == null) {
                throw new NullPointerException("settings for " + featureId);
            }
            LinkedHashMap<String, Object> settingCopy = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, ?> settingEntry : sourceSettings.entrySet()) {
                String settingId = ConfigNames.requireStableId(settingEntry.getKey(), "setting id");
                Object value = settingEntry.getValue();
                if (value == null) {
                    throw new NullPointerException("value for " + featureId + "." + settingId);
                }
                settingCopy.put(settingId, value);
            }
            featureCopy.put(featureId, Collections.unmodifiableMap(settingCopy));
        }
        this.features = Collections.unmodifiableMap(featureCopy);
    }

    public Map<String, Map<String, Object>> features() {
        return features;
    }

    public Map<String, Object> feature(String featureId) {
        return features.get(featureId);
    }

    public Object value(String featureId, String settingId) {
        Map<String, Object> settings = features.get(featureId);
        return settings == null ? null : settings.get(settingId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigSnapshot)) {
            return false;
        }
        ConfigSnapshot snapshot = (ConfigSnapshot) other;
        return features.equals(snapshot.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features);
    }
}
