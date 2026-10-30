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

import static pit12.feature.webui.WebUiJson.object;
import static pit12.feature.webui.WebUiJson.requiredString;
import static pit12.runtime.languages.Languages.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pit12.feature.profile.api.ProfileCreateSession;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.Profiles;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.RelationReadiness;
import pit12.feature.relation.api.Relations;
import pit12.feature.swap.api.SwapBindings;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.Setting;
import pit12.runtime.languages.Languages;
import pit12.shared.input.Keybind;
import pit12.shared.result.OperationResult;

final class WebUiApi {
    private static final String HINT_MESSAGE =
            source("{0} is available. Open Settings to change your language.");
    private static final String HINT_GOT_IT = source("Got it");
    private static final String HINT_HIDE = source("Don't show again");
    private static final String HINT_SWITCH = source("Switch language");
    private final ConfigCatalog catalog;
    private final ConfigCatalog settings;
    private final WebUiConfig config;
    private final Profiles profiles;
    private final Relations relations;
    private final SwapBindings swapBindings;
    private final Languages language;
    private final WebUiPreferences preferences;
    private final WebUiState state;
    private final SettingsTransfer transfer;
    private final ConfigChangeListener configListener;
    private final ConfigChangeListener settingsListener;
    private final Runnable languageListener;
    private final Runnable profilesListener;
    private final Runnable relationsListener;
    private final Runnable swapBindingsListener;
    private volatile Map<String, Object> catalogCache;

    WebUiApi(ConfigCatalog catalog, ConfigCatalog settings, WebUiConfig config, Profiles profiles,
            Relations relations, SwapBindings swapBindings, Languages language,
            WebUiPreferences preferences, WebUiEvents events) {
        this.catalog = catalog;
        this.settings = settings;
        this.config = config;
        this.profiles = profiles;
        this.relations = relations;
        this.swapBindings = swapBindings;
        this.language = language;
        this.preferences = preferences;
        configListener = changes -> {
            catalogCache = null;
            changes.forEach((featureId, settingId) -> events.changed("/api/settings/" + featureId));
        };
        settingsListener = changes -> {
            catalogCache = null;
            changes.forEach((featureId, settingId) -> events.changed("/api/settings/" + featureId));
            if (changes.affects(config.id(), config.languageHint().id()))
                events.changed("/api/preferences");
        };
        languageListener = () -> {
            catalogCache = null;
            events.changed("/api/settings");
        };
        profilesListener = () -> events.changed("/api/profiles");
        relationsListener = () -> events.changed("/api/relations");
        swapBindingsListener = () -> events.changed("/api/transfer");
        state = new WebUiState(catalog, config, profiles, relations, swapBindings, language,
                preferences);
        transfer = new SettingsTransfer(profiles, relations, swapBindings);
    }

    void start() {
        catalog.addListener(configListener);
        settings.addListener(settingsListener);
        language.addListener(languageListener);
        profiles.addListener(profilesListener);
        relations.addChangeListener(relationsListener);
        swapBindings.addChangeListener(swapBindingsListener);
    }

    void stop() {
        catalog.removeListener(configListener);
        settings.removeListener(settingsListener);
        language.removeListener(languageListener);
        profiles.removeListener(profilesListener);
        relations.removeChangeListener(relationsListener);
        swapBindings.removeChangeListener(swapBindingsListener);
        catalogCache = null;
    }

    void register(WebUiRoutes routes) {
        routes.registerCached("GET", "/api/settings", request -> catalogCache,
                request -> catalogState());
        routes.register("GET", "/api/settings/values", 0, request -> state.values());
        routes.register("GET", "/api/settings/{featureId}", 0,
                request -> state.values(feature(request.parameters.get("featureId"))));
        routes.register("PUT", "/api/settings/{featureId}/{settingId}", 8192, request -> {
            FeatureConfig feature = feature(request.parameters.get("featureId"));
            changeSetting(feature, request.parameters.get("settingId"), request.body);
            return state.values(feature);
        });
        routes.register("DELETE", "/api/settings", 0, request -> {
            requireSuccess(profiles.resetActive(), "reset");
            return state.values();
        });
        routes.register("GET", "/api/preferences", 0, request -> state.preferences());
        routes.register("PATCH", "/api/preferences", 32768, request -> {
            changePreferences(request.body);
            return state.preferences();
        });
        routes.register("GET", "/api/links", 0, request -> state.links());
        routes.register("GET", "/api/profiles", 0, request -> state.profiles());
        routes.register("POST", "/api/profiles", 8192, request -> {
            createProfile(request.body);
            return state.profiles();
        });
        routes.register("PATCH", "/api/profiles/{id}", 8192, request -> {
            requireSuccess(profiles.rename(UUID.fromString(request.parameters.get("id")),
                    requiredString(request.body, "name")), "rename");
            return state.profiles();
        });
        routes.register("DELETE", "/api/profiles/{id}", 0, request -> {
            requireSuccess(profiles.delete(UUID.fromString(request.parameters.get("id"))),
                    "delete");
            return state.profiles();
        });
        routes.register("PUT", "/api/profiles/active", 8192, request -> {
            requireSuccess(profiles.switchTo(UUID.fromString(requiredString(request.body, "id"))),
                    "switch");
            return state.profiles();
        });
        routes.register("GET", "/api/relations", 0, request -> state.relations());
        routes.register("PATCH", "/api/relations", 32768, request -> {
            boolean adding = request.body.has("add");
            if (adding == request.body.has("remove"))
                throw new IllegalArgumentException("Choose one relation change");
            List<Object> results = changeRelations(request.body, adding ? "add" : "remove");
            return object("relations", state.relations(), "results", results);
        });
        routes.register("GET", "/api/languages/{locale}", 0,
                request -> language.texts(request.parameters.get("locale")));
        routes.register("GET", "/api/languages/{locale}/hint", 0,
                request -> languageHint(request.parameters.get("locale")));
        routes.register("GET", "/api/transfer", 0, request -> {
            if (request.query.isEmpty())
                return state.transfer();
            JsonObject selection = new JsonObject();
            for (String key : new String[] {"profiles", "relations"}) {
                JsonArray entries = new JsonArray();
                String value = request.query.get(key);
                if (value != null && !value.isEmpty()) {
                    for (String entry : value.split(","))
                        entries.add(new JsonPrimitive(entry));
                }
                selection.add(key, entries);
            }
            String includeSwap = request.query.getOrDefault("swapBindings", "false");
            if (!"true".equals(includeSwap) && !"false".equals(includeSwap))
                throw new IllegalArgumentException("Expected a boolean");
            selection.addProperty("swapBindings", Boolean.parseBoolean(includeSwap));
            return transfer.exportData(selection);
        });
        routes.register("POST", "/api/transfer", 4 * 1024 * 1024,
                request -> transfer.preview(request.body));
        routes.register("PUT", "/api/transfer", 4 * 1024 * 1024, request -> {
            transfer.apply(request.body);
            return null;
        });
    }

    private Map<String, Object> languageHint(String locale) {
        String name = language.name(locale);
        return object("message", language.text(locale, HINT_MESSAGE).replace("{0}", name), "gotIt",
                language.text(locale, HINT_GOT_IT), "hide", language.text(locale, HINT_HIDE),
                "switch", language.text(locale, HINT_SWITCH));
    }

    private Map<String, Object> catalogState() {
        Map<String, Object> cached = catalogCache;
        if (cached == null) {
            cached = state.catalog();
            catalogCache = cached;
        }
        return cached;
    }

    private FeatureConfig feature(String id) {
        FeatureConfig feature = config.id().equals(id) ? config : catalog.feature(id);
        if (feature == null)
            throw new IllegalArgumentException("Unknown feature");
        return feature;
    }

    private void changePreferences(JsonObject request) {
        if (!preferences.ready())
            throw new IllegalArgumentException("Configuration interface settings are unavailable");
        for (Map.Entry<String, JsonElement> entry : request.entrySet()) {
            if (!"favorites".equals(entry.getKey()) && !"languageHint".equals(entry.getKey()))
                throw new IllegalArgumentException("Unknown interface preference");
        }
        JsonElement favorites = request.get("favorites");
        if (favorites != null) {
            if (!favorites.isJsonObject())
                throw new IllegalArgumentException("Expected favorite changes");
            for (Map.Entry<String, JsonElement> entry : favorites.getAsJsonObject().entrySet()) {
                if (catalog.feature(entry.getKey()) == null)
                    throw new IllegalArgumentException("Unknown feature");
                requireBoolean(entry.getValue());
            }
        }
        JsonElement hint = request.get("languageHint");
        if (hint != null)
            requireBoolean(hint);
        if (favorites != null) {
            for (Map.Entry<String, JsonElement> entry : favorites.getAsJsonObject().entrySet())
                preferences.setFavorite(entry.getKey(), entry.getValue().getAsBoolean());
        }
        if (hint != null)
            config.languageHint().set(hint.getAsBoolean());
    }

    private static void requireBoolean(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected a boolean");
    }

    private void createProfile(JsonObject request) {
        String name = requiredString(request, "name");
        ProfileMutationResult result = profiles.beginCreate();
        requireSuccess(result, "begin creating");
        ProfileCreateSession session = result.createSession();
        try {
            session.setName(name);
            requireSuccess(session.commit(), "create");
        } finally {
            if (!session.isClosed())
                session.cancel();
        }
    }

    private static void requireSuccess(ProfileMutationResult result, String action) {
        if (!result.succeeded())
            throw new IllegalArgumentException(
                    "Could not " + action + " profile: " + result.status());
    }

    private void changeSetting(FeatureConfig feature, String settingId, JsonObject request) {
        if (feature == config && !preferences.ready())
            throw new IllegalArgumentException("Configuration interface settings are unavailable");
        JsonElement value = request.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected a setting value");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if ("enabled".equals(settingId) && feature.toggleable()) {
            if (!primitive.isBoolean()) {
                throw new IllegalArgumentException("Expected a boolean");
            }
            feature.setEnabled(primitive.getAsBoolean());
            return;
        }
        for (ConfigOption<?> option : feature.options()) {
            if (!option.setting().id().equals(settingId)) {
                continue;
            }
            Object candidate;
            if (option.kind() == ConfigOption.Kind.KEYBIND) {
                if (!primitive.isString()) {
                    throw new IllegalArgumentException("Expected a key name");
                }
                candidate = Keybind.parse(primitive.getAsString());
            } else if (primitive.isBoolean()) {
                candidate = primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                candidate = primitive.getAsNumber();
            } else {
                candidate = primitive.getAsString();
            }
            if (option.setting() == config.language()) {
                if (!(candidate instanceof String)) {
                    throw new IllegalArgumentException("Expected a language code");
                }
                language.name((String) candidate);
                candidate = ((String) candidate).toLowerCase(Locale.ROOT);
            }
            setValue(option.setting(), candidate);
            return;
        }
        throw new IllegalArgumentException("Unknown setting");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setValue(Setting<?> setting, Object value) {
        ((Setting) setting).set(value);
    }

    private List<Object> changeRelations(JsonObject request, String action) {
        if (relations.readiness() != RelationReadiness.READY) {
            throw new IllegalArgumentException("Relations are not ready");
        }
        Relation relation = Relation.valueOf(requiredString(request, "relation"));
        if (relation == Relation.NONE) {
            throw new IllegalArgumentException("Choose Friend or Enemy");
        }
        JsonElement value = request.get(action);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Expected a list of players");
        }
        JsonArray entries = value.getAsJsonArray();
        if (entries.size() > 100) {
            throw new IllegalArgumentException("Select at most 100 players at a time");
        }
        List<Object> results = new ArrayList<>(Collections.nCopies(entries.size(), null));
        List<RelationEntry> changes = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> existing = new HashSet<>();
        if ("add".equals(action)) {
            for (RelationEntry saved : relations.entries(relation)) {
                if (saved.playerId() != null)
                    existing.add(saved.name().toLowerCase(Locale.ROOT));
            }
        }
        for (int index = 0; index < entries.size(); index++) {
            JsonElement element = entries.get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Expected a player entry");
            }
            JsonObject entry = element.getAsJsonObject();
            String name = requiredString(entry, "name").trim();
            JsonElement idValue = entry.get("uuid");
            UUID id = null;
            if (idValue != null && !idValue.isJsonNull()) {
                if ("add".equals(action)) {
                    throw new IllegalArgumentException("Cannot add a player by UUID");
                }
                id = UUID.fromString(requiredString(entry, "uuid"));
            }
            String identity = id == null ? name.toLowerCase(Locale.ROOT) : id.toString();
            if (name.isEmpty() || name.length() > 48
                    || id == null && "add".equals(action) && !name.matches("[A-Za-z0-9_]{1,48}")) {
                results.set(index, object("name", name, "message", "Invalid player name"));
            } else if (!seen.add(identity)) {
                results.set(index, object("name", name, "message", "Duplicate entry skipped"));
            } else if ("add".equals(action) && existing.contains(identity)) {
                results.set(index, object("name", name, "message", "Already on this list"));
            } else {
                changes.add(new RelationEntry(id, name, relation));
                positions.add(index);
            }
        }
        OperationResult<List<OperationResult<Void>>> batch =
                relations.changeMany(relation, action, changes);
        if (!batch.succeeded())
            throw new IllegalArgumentException(
                    "Could not " + action + " relations: " + batch.status());
        List<OperationResult<Void>> messages = batch.value();
        for (int index = 0; index < messages.size(); index++) {
            results.set(positions.get(index), object("name", changes.get(index).name(), "message",
                    messages.get(index).message()));
        }
        return results;
    }
}
