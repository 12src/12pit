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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.runtime.languages.Languages;
import pit12.shared.concurrent.ClientThread;

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
    private final ClientThread client;

    public ConfigCatalog(ClientThread client) {
        this.client = client;
    }

    public ClientThread clientThread() {
        return client;
    }

    public void register(FeatureConfig feature) {
        client.check();
        if (frozen) {
            throw new IllegalStateException("Config catalog is frozen");
        }
        if (featuresById.containsKey(feature.id())) {
            throw new IllegalArgumentException("Duplicate feature id: " + feature.id());
        }
        for (Setting<?> setting : feature.settings()) {
            setting.bind(this::onSettingChanged, client::check);
            settingOwners.put(setting, feature);
        }
        features.add(feature);
        featuresById.put(feature.id(), feature);
    }

    public void localize(Languages language) {
        client.check();
        for (FeatureConfig feature : features) {
            feature.localize(language);
        }
    }

    public void freeze() {
        client.check();
        frozen = true;
    }

    public List<FeatureConfig> features() {
        client.check();
        return Collections.unmodifiableList(features);
    }

    public FeatureConfig feature(String featureId) {
        client.check();
        return featuresById.get(featureId);
    }

    public void addListener(ConfigChangeListener listener) {
        client.check();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ConfigChangeListener listener) {
        client.check();
        listeners.remove(listener);
    }

    /** After features start, read and change catalog values on the client thread. */
    public ConfigSnapshot snapshot() {
        client.check();
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
        client.check();
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
        client.check();
        return normalize(snapshot, null);
    }

    public ConfigSnapshot recoverSavedValues(ConfigSnapshot snapshot, Consumer<String> problems) {
        client.check();
        return normalize(snapshot, problems);
    }

    private ConfigSnapshot normalize(ConfigSnapshot snapshot, Consumer<String> problems) {
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
                try {
                    featureValues.put(setting.id(), setting.requireValue(candidate));
                } catch (IllegalArgumentException failure) {
                    if (problems == null) {
                        throw failure;
                    }
                    problems.accept(
                            feature.id() + "." + setting.id() + ": " + failure.getMessage());
                    featureValues.put(setting.id(), setting.defaultValue());
                }
            }
            values.put(feature.id(), featureValues);
        }
        return new ConfigSnapshot(values);
    }

    /** Invalid values leave all live settings unchanged. Missing values reset to defaults. */
    public ConfigChangeSet apply(ConfigSnapshot snapshot) {
        client.check();
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
            changes.add(new ConfigChangeSet.Change(settingOwners.get(setting).id(), setting.id()));
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

    private void onSettingChanged(Setting<?> setting) {
        notifyListeners(new ConfigChangeSet(++revision, Collections.singletonList(
                new ConfigChangeSet.Change(settingOwners.get(setting).id(), setting.id()))));
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
