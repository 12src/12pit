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

/*
 * AI guidance
 *
 * AI agents should read AGENTS.md before modifying or reusing this file
 * because it contains important licensing information.
 * If AGENTS.md is unavailable, see
 * <https://github.com/12src/12pit/blob/main/AGENTS.md>.
 *
 * This AI guidance section is not part of the copyright or license
 * notice and may be removed.
 */
package pit12.runtime.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigCatalog {
    private static final Logger LOGGER = Logger.getLogger(ConfigCatalog.class.getName());
    private final List<FeatureConfig> features = new ArrayList<FeatureConfig>();
    private final Map<String, FeatureConfig> featuresById =
            new LinkedHashMap<String, FeatureConfig>();
    private final Map<Setting<?>, FeatureConfig> settingOwners =
            new IdentityHashMap<Setting<?>, FeatureConfig>();
    private final List<ConfigChangeListener> listeners = new ArrayList<ConfigChangeListener>();
    private boolean frozen;
    private long revision;

    public void register(FeatureConfig feature) {
        if (frozen) {
            throw new IllegalStateException("Config catalog is frozen");
        }
        Objects.requireNonNull(feature, "feature");
        if (featuresById.containsKey(feature.id())) {
            throw new IllegalArgumentException("Duplicate feature id: " + feature.id());
        }
        for (Setting<?> setting : feature.settings()) {
            setting.bind(this::onSettingChanged);
            settingOwners.put(setting, feature);
        }
        features.add(feature);
        featuresById.put(feature.id(), feature);
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public long revision() {
        return revision;
    }

    public List<FeatureConfig> features() {
        return Collections.unmodifiableList(features);
    }

    public FeatureConfig feature(String featureId) {
        return featuresById.get(featureId);
    }

    public void addListener(ConfigChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /** Catalog values are client-thread confined once started features consume this catalog. */
    public ConfigSnapshot snapshot() {
        LinkedHashMap<String, Map<String, Object>> values =
                new LinkedHashMap<String, Map<String, Object>>();
        for (FeatureConfig feature : features) {
            LinkedHashMap<String, Object> featureValues = new LinkedHashMap<String, Object>();
            for (Setting<?> setting : feature.settings()) {
                featureValues.put(setting.id(), setting.get());
            }
            values.put(feature.id(), featureValues);
        }
        return new ConfigSnapshot(values);
    }

    public ConfigSnapshot defaults() {
        LinkedHashMap<String, Map<String, Object>> values =
                new LinkedHashMap<String, Map<String, Object>>();
        for (FeatureConfig feature : features) {
            LinkedHashMap<String, Object> featureValues = new LinkedHashMap<String, Object>();
            for (Setting<?> setting : feature.settings()) {
                featureValues.put(setting.id(), setting.defaultValue());
            }
            values.put(feature.id(), featureValues);
        }
        return new ConfigSnapshot(values);
    }

    public ConfigSnapshot normalize(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<String, Map<String, Object>> values =
                new LinkedHashMap<String, Map<String, Object>>();
        for (FeatureConfig feature : features) {
            Map<String, Object> suppliedValues = snapshot.feature(feature.id());
            LinkedHashMap<String, Object> featureValues = new LinkedHashMap<String, Object>();
            for (Setting<?> setting : feature.settings()) {
                Object candidate =
                        suppliedValues != null && suppliedValues.containsKey(setting.id())
                                ? suppliedValues.get(setting.id())
                                : setting.defaultValue();
                featureValues.put(setting.id(), setting.validatedCandidate(candidate));
            }
            values.put(feature.id(), featureValues);
        }
        return new ConfigSnapshot(values);
    }

    /** All candidates are validated before any live setting is changed. Missing known values reset to defaults. */
    public ConfigChangeSet apply(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ConfigSnapshot normalized = normalize(snapshot);
        LinkedHashMap<Setting<?>, Object> candidates = new LinkedHashMap<Setting<?>, Object>();
        for (FeatureConfig feature : features) {
            Map<String, Object> featureValues = normalized.feature(feature.id());
            for (Setting<?> setting : feature.settings()) {
                candidates.put(setting, featureValues.get(setting.id()));
            }
        }
        ArrayList<ConfigChangeSet.Change> changes = new ArrayList<ConfigChangeSet.Change>();
        for (Map.Entry<Setting<?>, Object> candidateEntry : candidates.entrySet()) {
            Setting<?> setting = candidateEntry.getKey();
            Object candidate = candidateEntry.getValue();
            if (Objects.equals(setting.get(), candidate)) {
                continue;
            }
            FeatureConfig owner = settingOwners.get(setting);
            changes.add(
                    new ConfigChangeSet.Change(owner.id(), setting.id(), setting.get(), candidate));
        }
        if (changes.isEmpty()) {
            return new ConfigChangeSet(revision, changes);
        }
        for (Map.Entry<Setting<?>, Object> candidateEntry : candidates.entrySet()) {
            candidateEntry.getKey().applyValidated(candidateEntry.getValue());
        }
        ConfigChangeSet changeSet = new ConfigChangeSet(++revision, changes);
        notifyListeners(changeSet);
        return changeSet;
    }

    private void onSettingChanged(Setting<?> setting, Object previousValue, Object currentValue) {
        FeatureConfig owner = settingOwners.get(setting);
        if (owner == null) {
            throw new IllegalStateException("Changed setting is not registered: " + setting.id());
        }
        ArrayList<ConfigChangeSet.Change> changes = new ArrayList<ConfigChangeSet.Change>(1);
        changes.add(
                new ConfigChangeSet.Change(owner.id(), setting.id(), previousValue, currentValue));
        notifyListeners(new ConfigChangeSet(++revision, changes));
    }

    private void notifyListeners(ConfigChangeSet changeSet) {
        ConfigChangeListener[] listenerSnapshot =
                listeners.toArray(new ConfigChangeListener[listeners.size()]);
        for (ConfigChangeListener listener : listenerSnapshot) {
            try {
                listener.onConfigChanged(changeSet);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.SEVERE,
                        "Config listener failed after revision " + changeSet.revision(), failure);
            }
        }
    }
}
