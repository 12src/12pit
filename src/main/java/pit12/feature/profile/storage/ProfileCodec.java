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
package pit12.feature.profile.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pit12.runtime.config.ConfigSnapshot;

public final class ProfileCodec {
    public static final int SCHEMA_VERSION = 1;

    public static final class DecodeResult {
        private final StoredProfile profile;
        private final List<String> warnings;

        DecodeResult(StoredProfile profile, List<String> warnings) {
            this.profile = profile;
            this.warnings = warnings;
        }

        public StoredProfile profile() {
            return profile;
        }

        public List<String> warnings() {
            return warnings;
        }
    }
    public static final class UnsupportedSchemaException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        UnsupportedSchemaException(int version) {
            super("schemaVersion " + version + " is newer than supported version "
                    + SCHEMA_VERSION);
        }
    }

    private final ProfileSchema schema;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProfileCodec(ProfileSchema schema) {
        this.schema = schema;
    }

    public DecodeResult decode(UUID expectedId, Reader reader) {
        // Minecraft's Gson 2.2.4 predates the static parseReader helpers in newer Gson releases.
        JsonElement parsed = new JsonParser().parse(reader);
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IllegalArgumentException("root must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        int schemaVersion = requiredInt(root, "schemaVersion", 0);
        if (schemaVersion > SCHEMA_VERSION) {
            throw new UnsupportedSchemaException(schemaVersion);
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schemaVersion " + schemaVersion);
        }
        UUID id = UUID.fromString(requiredString(root, "id"));
        if (!expectedId.equals(id)) {
            throw new IllegalArgumentException("id does not match filename UUID");
        }
        String name = requiredString(root, "name").trim();
        if (name.isEmpty() || name.length() > 48) {
            throw new IllegalArgumentException("name must contain 1 to 48 characters");
        }
        int order = requiredInt(root, "order", 0);
        if (order < 0) {
            throw new IllegalArgumentException("order must be non-negative");
        }
        long updatedAt = optionalLong(root, "updatedAt", 0L);
        ArrayList<String> warnings = new ArrayList<String>();
        LinkedHashMap<String, Map<String, Object>> configValues =
                new LinkedHashMap<String, Map<String, Object>>();
        JsonElement featuresElement = root.get("features");
        JsonObject features = featuresElement != null && featuresElement.isJsonObject()
                ? featuresElement.getAsJsonObject()
                : new JsonObject();
        if (featuresElement != null && !featuresElement.isJsonObject()) {
            warnings.add("$.features must be an object; known settings use defaults");
        }
        for (Map.Entry<String, Map<String, ProfileSchema.ValueType>> featureEntry : schema
                .features().entrySet()) {
            LinkedHashMap<String, Object> settingValues = new LinkedHashMap<String, Object>();
            JsonElement featureElement = features.get(featureEntry.getKey());
            JsonObject featureObject = featureElement != null && featureElement.isJsonObject()
                    ? featureElement.getAsJsonObject()
                    : null;
            if (featureElement != null && !featureElement.isJsonObject()) {
                warnings.add("$.features." + featureEntry.getKey() + " must be an object");
            }
            if (featureObject != null) {
                for (Map.Entry<String, ProfileSchema.ValueType> settingEntry : featureEntry
                        .getValue().entrySet()) {
                    JsonElement value = featureObject.get(settingEntry.getKey());
                    if (value == null || value.isJsonNull()) {
                        continue;
                    }
                    if (settingEntry.getValue() == ProfileSchema.ValueType.BOOLEAN
                            && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                        settingValues.put(settingEntry.getKey(),
                                Boolean.valueOf(value.getAsBoolean()));
                    } else if (settingEntry.getValue() == ProfileSchema.ValueType.INTEGER
                            && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                        try {
                            settingValues.put(settingEntry.getKey(),
                                    Integer.valueOf(Integer.parseInt(value.getAsString())));
                        } catch (NumberFormatException failure) {
                            warnings.add("$.features." + featureEntry.getKey() + "."
                                    + settingEntry.getKey()
                                    + " must be an integer; the default is used");
                        }
                    } else if (settingEntry.getValue() != ProfileSchema.ValueType.UNSUPPORTED) {
                        warnings.add(
                                "$.features." + featureEntry.getKey() + "." + settingEntry.getKey()
                                        + " has the wrong value type; the default is used");
                    }
                }
            }
            configValues.put(featureEntry.getKey(), settingValues);
        }
        return new DecodeResult(new StoredProfile(id, name, order, updatedAt, 0L,
                new ConfigSnapshot(configValues), copy(root).getAsJsonObject()), warnings);
    }

    public String encode(StoredProfile profile) {
        JsonObject root = profile.preservedRoot() == null ? new JsonObject()
                : copy(profile.preservedRoot()).getAsJsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(SCHEMA_VERSION));
        root.addProperty("id", profile.id().toString());
        root.addProperty("name", profile.name());
        root.addProperty("order", Integer.valueOf(profile.order()));
        root.remove("visible");
        root.addProperty("updatedAt", Long.valueOf(profile.updatedAt()));
        if (!root.has("binding")) {
            root.add("binding", JsonNull.INSTANCE);
        }
        JsonElement oldFeatures = root.get("features");
        JsonObject features =
                oldFeatures != null && oldFeatures.isJsonObject() ? oldFeatures.getAsJsonObject()
                        : new JsonObject();
        for (Map.Entry<String, Map<String, ProfileSchema.ValueType>> featureEntry : schema
                .features().entrySet()) {
            JsonElement oldFeature = features.get(featureEntry.getKey());
            JsonObject featureObject =
                    oldFeature != null && oldFeature.isJsonObject() ? oldFeature.getAsJsonObject()
                            : new JsonObject();
            Map<String, Object> values = profile.config().feature(featureEntry.getKey());
            if (values != null) {
                for (Map.Entry<String, ProfileSchema.ValueType> settingEntry : featureEntry
                        .getValue().entrySet()) {
                    if (settingEntry.getValue() == ProfileSchema.ValueType.BOOLEAN) {
                        Object value = values.get(settingEntry.getKey());
                        if (value instanceof Boolean) {
                            featureObject.addProperty(settingEntry.getKey(), (Boolean) value);
                        }
                    } else if (settingEntry.getValue() == ProfileSchema.ValueType.INTEGER) {
                        Object value = values.get(settingEntry.getKey());
                        if (value instanceof Number) {
                            featureObject.addProperty(settingEntry.getKey(), (Number) value);
                        }
                    }
                }
            }
            features.add(featureEntry.getKey(), featureObject);
        }
        root.add("features", features);
        return gson.toJson(root);
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("$." + key + " must be a string");
        }
        return value.getAsString();
    }

    private static int requiredInt(JsonObject object, String key, int minimum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("$." + key + " must be an integer");
        }
        try {
            int parsed = Integer.parseInt(value.getAsString());
            if (parsed < minimum) {
                throw new IllegalArgumentException("$." + key + " must be at least " + minimum);
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("$." + key + " must be an integer", failure);
        }
    }

    private static long optionalLong(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("$." + key + " must be an integer");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("$." + key + " must be an integer", failure);
        }
    }

    public static JsonElement copy(JsonElement source) {
        if (source == null || source.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (source.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet()) {
                result.add(entry.getKey(), copy(entry.getValue()));
            }
            return result;
        }
        if (source.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : source.getAsJsonArray()) {
                result.add(copy(element));
            }
            return result;
        }
        JsonPrimitive primitive = source.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return new JsonPrimitive(Boolean.valueOf(primitive.getAsBoolean()));
        }
        if (primitive.isNumber()) {
            return new JsonPrimitive(primitive.getAsNumber());
        }
        return new JsonPrimitive(primitive.getAsString());
    }
}
