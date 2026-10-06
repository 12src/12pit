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
package pit12.feature.webui;

import static pit12.runtime.languages.Languages.source;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigChangeSet;
import pit12.runtime.config.ConfigSnapshot;
import pit12.runtime.config.Setting;
import pit12.shared.storage.AtomicFile;

final class WebUiPreferences implements ConfigChangeListener {
    private static final Logger LOGGER = Logger.getLogger(WebUiPreferences.class.getName());
    private final ConfigCatalog catalog;
    private final WebUiConfig config;
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Set<String> editedWhileLoading = new LinkedHashSet<>();
    private ScheduledExecutorService worker;
    private ScheduledFuture<?> pendingWrite;
    private Runnable changed;
    private boolean ready;
    private boolean applying;
    private String problem;
    private long generation;

    WebUiPreferences(ConfigCatalog catalog, WebUiConfig config, Path path) {
        this.catalog = catalog;
        this.config = config;
        this.path = path;
    }

    void start(Runnable changed) {
        if (worker != null)
            return;
        this.changed = changed;
        ready = false;
        problem = null;
        editedWhileLoading.clear();
        long activeGeneration = ++generation;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "12pit-webui-settings");
            thread.setDaemon(true);
            return thread;
        });
        catalog.addListener(this);
        worker.execute(() -> {
            try {
                boolean missing = Files.notExists(path);
                JsonObject loaded;
                if (missing) {
                    loaded = new JsonObject();
                    loaded.addProperty("schemaVersion", 1);
                } else {
                    loaded = read(path);
                }
                dispatch(activeGeneration, () -> {
                    try {
                        applyLoaded(loaded);
                        ready = true;
                        if (missing || !editedWhileLoading.isEmpty())
                            save();
                        editedWhileLoading.clear();
                        changed.run();
                    } catch (RuntimeException failure) {
                        failed(source("Could not load Web UI settings"), failure);
                    }
                });
            } catch (IOException | RuntimeException failure) {
                dispatch(activeGeneration,
                        () -> failed(source("Could not load Web UI settings"), failure));
            }
        });
    }

    void stop() {
        catalog.removeListener(this);
        if (worker == null)
            return;
        if (pendingWrite != null)
            pendingWrite.cancel(false);
        if (ready) {
            String text = encode();
            long activeGeneration = generation;
            worker.execute(() -> write(text, activeGeneration));
        }
        generation++;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(2000L, TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
                if (!worker.awaitTermination(2000L, TimeUnit.MILLISECONDS))
                    throw new IllegalStateException("Web UI settings worker did not stop");
            }
        } catch (InterruptedException failure) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while saving Web UI settings", failure);
        }
        worker = null;
        pendingWrite = null;
        ready = false;
    }

    boolean ready() {
        return ready;
    }

    String problem() {
        return problem;
    }

    List<String> favorites() {
        return new ArrayList<>(favorites);
    }

    void setFavorite(String featureId, boolean favorite) {
        if (!ready)
            throw new IllegalArgumentException(source("Web UI settings are unavailable"));
        if (favorite ? favorites.add(featureId) : favorites.remove(featureId)) {
            save();
            changed.run();
        }
    }

    @Override
    public void onConfigChanged(ConfigChangeSet changes) {
        if (applying)
            return;
        if (!ready) {
            for (Setting<?> setting : config.settings()) {
                if (changes.affects(config.id(), setting.id()))
                    editedWhileLoading.add(setting.id());
            }
            return;
        }
        save();
    }

    private void applyLoaded(JsonObject root) {
        JsonElement version = root.get("schemaVersion");
        if (version == null || !version.isJsonPrimitive()
                || !version.getAsJsonPrimitive().isNumber() || !"1".equals(version.getAsString()))
            throw new IllegalArgumentException("Unsupported Web UI settings version");
        Set<String> loadedFavorites = new LinkedHashSet<>();
        JsonElement entries = root.get("favorites");
        if (entries != null) {
            if (!entries.isJsonArray())
                throw new IllegalArgumentException("Expected a favorite feature list");
            for (JsonElement entry : entries.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                        || entry.getAsString().isEmpty())
                    throw new IllegalArgumentException("Invalid favorite feature ID");
                loadedFavorites.add(entry.getAsString());
            }
        }
        JsonElement settings = root.get("settings");
        if (settings != null && !settings.isJsonObject())
            throw new IllegalArgumentException("Expected Web UI settings");
        Map<String, Object> values = new LinkedHashMap<>();
        for (Setting<?> setting : config.settings()) {
            if (editedWhileLoading.contains(setting.id())) {
                values.put(setting.id(), setting.get());
                continue;
            }
            JsonElement value =
                    settings == null ? null : settings.getAsJsonObject().get(setting.id());
            if (value == null)
                continue;
            if (!value.isJsonPrimitive())
                throw new IllegalArgumentException("Invalid Web UI setting: " + setting.id());
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (setting.storageType() == Setting.StorageType.BOOLEAN && primitive.isBoolean())
                values.put(setting.id(), primitive.getAsBoolean());
            else if (setting.storageType() == Setting.StorageType.INTEGER && primitive.isNumber())
                values.put(setting.id(), Integer.parseInt(primitive.getAsString()));
            else
                throw new IllegalArgumentException("Invalid Web UI setting: " + setting.id());
        }
        applying = true;
        try {
            catalog.apply(new ConfigSnapshot(Collections.singletonMap(config.id(), values)));
        } finally {
            applying = false;
        }
        favorites.clear();
        favorites.addAll(loadedFavorites);
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject())
                throw new IllegalArgumentException("Expected a JSON object");
            return root.getAsJsonObject();
        }
    }

    private String encode() {
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 1);
        JsonObject settings = new JsonObject();
        for (Setting<?> setting : config.settings()) {
            Object value = setting.get();
            if (value instanceof Boolean)
                settings.addProperty(setting.id(), (Boolean) value);
            else
                settings.addProperty(setting.id(), (Number) value);
        }
        document.add("settings", settings);
        JsonArray entries = new JsonArray();
        for (String favorite : favorites)
            entries.add(new JsonPrimitive(favorite));
        document.add("favorites", entries);
        return gson.toJson(document);
    }

    private void save() {
        if (pendingWrite != null)
            pendingWrite.cancel(false);
        String text = encode();
        long activeGeneration = generation;
        pendingWrite =
                worker.schedule(() -> write(text, activeGeneration), 250L, TimeUnit.MILLISECONDS);
    }

    private void write(String text, long activeGeneration) {
        try {
            AtomicFile.write(path, text);
            dispatch(activeGeneration, () -> {
                if (problem != null) {
                    problem = null;
                    changed.run();
                }
            });
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Could not save Web UI settings to " + path, failure);
            dispatch(activeGeneration, () -> {
                problem = source("Could not save Web UI settings");
                changed.run();
            });
        }
    }

    private void failed(String message, Exception failure) {
        problem = message;
        LOGGER.log(Level.WARNING, message + " from " + path, failure);
        changed.run();
    }

    private void dispatch(long activeGeneration, Runnable task) {
        catalog.clientThread().execute(() -> {
            if (worker != null && generation == activeGeneration)
                task.run();
        });
    }
}
