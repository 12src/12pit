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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigSnapshot {
    private final Map<String, Map<String, Object>> features;

    public ConfigSnapshot(Map<String, ? extends Map<String, ?>> features) {
        LinkedHashMap<String, Map<String, Object>> featureCopy =
                new LinkedHashMap<String, Map<String, Object>>();
        for (Map.Entry<String, ? extends Map<String, ?>> featureEntry : features.entrySet()) {
            featureCopy.put(featureEntry.getKey(), Collections
                    .unmodifiableMap(new LinkedHashMap<String, Object>(featureEntry.getValue())));
        }
        this.features = Collections.unmodifiableMap(featureCopy);
    }

    public Map<String, Object> feature(String featureId) {
        return features.get(featureId);
    }
}
