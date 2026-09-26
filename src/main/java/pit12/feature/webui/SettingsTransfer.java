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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.api.Profiles;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.Relations;

final class SettingsTransfer {
    private static final int VERSION = 1;
    private static final List<Relation> TYPES = Arrays.asList(Relation.FRIEND, Relation.ENEMY);
    private final Profiles profiles;
    private final Relations relations;
    private final Gson gson = new Gson();

    SettingsTransfer(Profiles profiles, Relations relations) {
        this.profiles = profiles;
        this.relations = relations;
    }

    JsonObject exportData(JsonObject request) {
        List<UUID> selected = ids(array(request, "profiles"));
        List<Relation> types = types(array(request, "relations"));
        requireSelection(selected, types);
        JsonObject data = new JsonObject();
        data.addProperty("schemaVersion", VERSION);
        JsonArray exported = new JsonArray();
        for (String text : profiles.exportProfiles(selected)) {
            exported.add(new JsonParser().parse(text));
        }
        data.add("profiles", exported);
        JsonObject groups = new JsonObject();
        for (Relation type : types) {
            requireRelations();
            groups.add(type.name(), entries(relations.entries(type)));
        }
        data.add("relations", groups);
        return data;
    }

    JsonObject preview(JsonObject request) {
        Bundle bundle = parseData(object(request, "data"));
        JsonArray summaries = new JsonArray();
        for (String text : bundle.profiles) {
            JsonObject profile = new JsonParser().parse(text).getAsJsonObject();
            JsonObject summary = new JsonObject();
            summary.addProperty("id", profile.get("id").getAsString());
            summary.addProperty("name", profile.get("name").getAsString());
            summaries.add(summary);
        }
        JsonObject groups = new JsonObject();
        for (Map.Entry<Relation, List<RelationEntry>> group : bundle.groups.entrySet()) {
            groups.addProperty(group.getKey().name(), group.getValue().size());
        }
        JsonObject result = new JsonObject();
        result.add("profiles", summaries);
        result.add("relations", groups);
        result.add("conflicts", conflicts(bundle));
        result.addProperty("fingerprint", fingerprint());
        return result;
    }

    void apply(JsonObject request) {
        Bundle bundle = parseData(object(request, "data"));
        List<UUID> selectedProfiles = ids(array(request, "profiles"));
        List<Relation> selectedTypes = types(array(request, "relations"));
        requireSelection(selectedProfiles, selectedTypes);
        if (!string(request, "fingerprint").equals(fingerprint())) {
            throw new IllegalArgumentException("Settings changed; preview the file again");
        }
        String mode = string(request, "mode");
        if (!"merge".equals(mode) && !"replace".equals(mode)) {
            throw new IllegalArgumentException("Choose a relation import mode");
        }
        Map<UUID, String> byId = new LinkedHashMap<UUID, String>();
        for (String text : bundle.profiles) {
            JsonObject profile = new JsonParser().parse(text).getAsJsonObject();
            byId.put(UUID.fromString(profile.get("id").getAsString()), text);
        }
        ArrayList<String> chosen = new ArrayList<String>();
        for (UUID id : selectedProfiles) {
            String text = byId.get(id);
            if (text == null) {
                throw new IllegalArgumentException("Profile is not in the file: " + id);
            }
            chosen.add(text);
        }
        for (Relation type : selectedTypes) {
            if (!bundle.groups.containsKey(type)) {
                throw new IllegalArgumentException("Relation group is not in the file");
            }
        }
        if (!chosen.isEmpty()) {
            profiles.validateImportProfiles(chosen);
        }
        JsonObject decisions = object(request, "resolutions");
        List<RelationEntry> resolved = resolve(bundle, selectedTypes, mode, decisions);
        if (!chosen.isEmpty()) {
            profiles.importProfiles(chosen);
        }
        if (!selectedTypes.isEmpty()) {
            relations.replaceAll(resolved);
        }
    }

    private Bundle parseData(JsonObject data) {
        JsonElement version = data.get("schemaVersion");
        if (version == null || !version.isJsonPrimitive()
                || !version.getAsJsonPrimitive().isNumber() || !"1".equals(version.getAsString())) {
            throw new IllegalArgumentException("Unsupported file version");
        }
        ArrayList<String> savedProfiles = new ArrayList<String>();
        for (JsonElement element : array(data, "profiles")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Profile must be an object");
            }
            savedProfiles.add(gson.toJson(element));
        }
        if (!savedProfiles.isEmpty()) {
            profiles.validateImportProfiles(savedProfiles);
        }
        EnumMap<Relation, List<RelationEntry>> groups =
                new EnumMap<Relation, List<RelationEntry>>(Relation.class);
        JsonObject source = object(data, "relations");
        for (Map.Entry<String, JsonElement> group : source.entrySet()) {
            Relation type;
            try {
                type = Relation.valueOf(group.getKey());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown relation group", invalid);
            }
            if (!TYPES.contains(type) || !group.getValue().isJsonArray()) {
                throw new IllegalArgumentException("Invalid relation group");
            }
            requireRelations();
            ArrayList<RelationEntry> entries = new ArrayList<RelationEntry>();
            for (JsonElement item : group.getValue().getAsJsonArray()) {
                if (!item.isJsonObject()) {
                    throw new IllegalArgumentException("Relation entry must be an object");
                }
                JsonObject value = item.getAsJsonObject();
                if (!type.name().equals(string(value, "relation"))) {
                    throw new IllegalArgumentException("Relation is in the wrong group");
                }
                JsonElement uuid = value.get("uuid");
                if (uuid == null) {
                    throw new IllegalArgumentException("Relation UUID is missing");
                }
                entries.add(new RelationEntry(
                        uuid.isJsonNull() ? null : UUID.fromString(string(value, "uuid")),
                        string(value, "name"), type));
            }
            groups.put(type, entries);
        }
        Set<UUID> ids = new HashSet<UUID>();
        Set<String> pending = new HashSet<String>();
        for (List<RelationEntry> group : groups.values()) {
            for (RelationEntry entry : group) {
                if (entry.playerId() == null ? !pending.add(entry.name().toLowerCase(Locale.ROOT))
                        : !ids.add(entry.playerId())) {
                    throw new IllegalArgumentException("Duplicate relation in file");
                }
            }
        }
        if (savedProfiles.isEmpty() && groups.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        return new Bundle(savedProfiles, groups);
    }

    private JsonArray conflicts(Bundle bundle) {
        JsonArray result = new JsonArray();
        if (bundle.groups.isEmpty()) {
            return result;
        }
        List<RelationEntry> local = localEntries();
        for (Relation type : TYPES) {
            List<RelationEntry> imported = bundle.groups.get(type);
            if (imported == null) {
                continue;
            }
            for (int index = 0; index < imported.size(); index++) {
                RelationEntry incoming = imported.get(index);
                List<RelationEntry> matches = matches(local, incoming);
                if (!conflict(matches, incoming)) {
                    continue;
                }
                JsonObject row = new JsonObject();
                row.addProperty("id", type.name() + ":" + index);
                row.addProperty("type", type.name());
                row.addProperty("kind", kind(matches, incoming));
                row.add("local", entries(matches));
                row.add("incoming", entry(incoming));
                result.add(row);
            }
        }
        return result;
    }

    private List<RelationEntry> resolve(Bundle bundle, List<Relation> selected, String mode,
            JsonObject decisions) {
        if (selected.isEmpty()) {
            return Collections.emptyList();
        }
        List<RelationEntry> local = localEntries();
        ArrayList<RelationEntry> result = new ArrayList<RelationEntry>();
        for (RelationEntry entry : local) {
            if (!"replace".equals(mode) || !selected.contains(entry.relation())) {
                result.add(entry);
            }
        }
        if ("replace".equals(mode)) {
            for (Relation type : selected) {
                result.addAll(bundle.groups.get(type));
            }
            validateResolved(result);
            return result;
        }
        for (Relation type : selected) {
            List<RelationEntry> imported = bundle.groups.get(type);
            for (int index = 0; index < imported.size(); index++) {
                RelationEntry incoming = imported.get(index);
                List<RelationEntry> matches = matches(local, incoming);
                if (conflict(matches, incoming)) {
                    String choice = string(decisions, type.name() + ":" + index);
                    if (!"local".equals(choice) && !"imported".equals(choice)) {
                        throw new IllegalArgumentException("Resolve every relation conflict");
                    }
                    if ("local".equals(choice)) {
                        for (RelationEntry entry : matches) {
                            if (!result.contains(entry)) {
                                result.add(entry);
                            }
                        }
                        continue;
                    }
                }
                result.removeAll(matches);
                result.add(incoming);
            }
        }
        validateResolved(result);
        return result;
    }

    private static void validateResolved(List<RelationEntry> result) {
        Set<UUID> ids = new HashSet<UUID>();
        Set<String> pending = new HashSet<String>();
        for (RelationEntry entry : result) {
            if (entry.playerId() == null ? !pending.add(entry.name().toLowerCase(Locale.ROOT))
                    : !ids.add(entry.playerId())) {
                throw new IllegalArgumentException(
                        "Selected relation groups contain duplicate player identities");
            }
        }
    }

    private String fingerprint() {
        List<UUID> all = new ArrayList<UUID>();
        for (ProfileSummary profile : profiles.snapshot().profiles()) {
            all.add(profile.id());
        }
        StringBuilder state = new StringBuilder(gson.toJson(profiles.exportProfiles(all)));
        state.append(profiles.snapshot().activeProfileId());
        for (Relation type : TYPES) {
            state.append(type).append(gson.toJson(entries(relations.entries(type))));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(state.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void requireRelations() {
        if (relations.readinessProblem() != null) {
            throw new IllegalArgumentException(relations.readinessProblem());
        }
    }

    private List<RelationEntry> localEntries() {
        requireRelations();
        ArrayList<RelationEntry> entries = new ArrayList<RelationEntry>();
        for (Relation type : TYPES) {
            entries.addAll(relations.entries(type));
        }
        return entries;
    }

    private static List<RelationEntry> matches(List<RelationEntry> local, RelationEntry incoming) {
        ArrayList<RelationEntry> found = new ArrayList<RelationEntry>();
        for (RelationEntry entry : local) {
            if (incoming.playerId() != null && incoming.playerId().equals(entry.playerId())
                    || incoming.name().equalsIgnoreCase(entry.name())) {
                found.add(entry);
            }
        }
        return found;
    }

    private static boolean conflict(List<RelationEntry> matches, RelationEntry incoming) {
        if (matches.isEmpty()) {
            return false;
        }
        if (matches.size() != 1) {
            return true;
        }
        RelationEntry local = matches.get(0);
        return local.relation() != incoming.relation() || !local.name().equals(incoming.name())
                || !java.util.Objects.equals(local.playerId(), incoming.playerId());
    }

    private static String kind(List<RelationEntry> matches, RelationEntry incoming) {
        for (RelationEntry entry : matches) {
            if (entry.relation() != incoming.relation()) {
                return "group";
            }
        }
        for (RelationEntry entry : matches) {
            if (!java.util.Objects.equals(entry.playerId(), incoming.playerId())) {
                return "identity";
            }
        }
        return "name";
    }

    private static JsonArray entries(List<RelationEntry> values) {
        JsonArray result = new JsonArray();
        for (RelationEntry value : values) {
            result.add(entry(value));
        }
        return result;
    }

    private static JsonObject entry(RelationEntry value) {
        JsonObject result = new JsonObject();
        if (value.playerId() == null) {
            result.add("uuid", com.google.gson.JsonNull.INSTANCE);
        } else {
            result.addProperty("uuid", value.playerId().toString());
        }
        result.addProperty("name", value.name());
        result.addProperty("relation", value.relation().name());
        return result;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Expected " + key + " object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Expected " + key + " list");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected " + key);
        }
        return value.getAsString();
    }

    private static List<UUID> ids(JsonArray values) {
        ArrayList<UUID> result = new ArrayList<UUID>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Expected profile ID");
            }
            UUID id = UUID.fromString(value.getAsString());
            if (result.contains(id)) {
                throw new IllegalArgumentException("Duplicate profile selection");
            }
            result.add(id);
        }
        return result;
    }

    private static List<Relation> types(JsonArray values) {
        ArrayList<Relation> result = new ArrayList<Relation>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Expected relation group");
            }
            Relation type = Relation.valueOf(value.getAsString());
            if (!TYPES.contains(type) || result.contains(type)) {
                throw new IllegalArgumentException("Invalid relation selection");
            }
            result.add(type);
        }
        return result;
    }

    private static void requireSelection(List<UUID> profiles, List<Relation> relations) {
        if (profiles.isEmpty() && relations.isEmpty()) {
            throw new IllegalArgumentException("Select at least one item");
        }
    }

    private static final class Bundle {
        final List<String> profiles;
        final Map<Relation, List<RelationEntry>> groups;

        Bundle(List<String> profiles, Map<Relation, List<RelationEntry>> groups) {
            this.profiles = profiles;
            this.groups = groups;
        }
    }
}
