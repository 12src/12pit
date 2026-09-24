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
package pit12.feature.relation.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;

public final class RelationStore {
    private static final int SCHEMA_VERSION = 1;
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public RelationStore(Path path) {
        this.path = path;
    }

    public List<RelationEntry> load() throws IOException {
        if (Files.notExists(path)) {
            return new ArrayList<RelationEntry>();
        }
        try (java.io.BufferedReader reader =
                Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Relation file must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement version = root.get("schemaVersion");
            if (version == null || !version.isJsonPrimitive()
                    || !version.getAsJsonPrimitive().isNumber()
                    || Integer.parseInt(version.getAsString()) != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported relation schema version");
            }
            JsonElement entries = root.get("entries");
            if (entries == null || !entries.isJsonArray()) {
                throw new IllegalArgumentException("Relation entries must be an array");
            }
            List<RelationEntry> loaded = new ArrayList<RelationEntry>();
            Set<UUID> ids = new HashSet<UUID>();
            Set<String> pendingNames = new HashSet<String>();
            for (JsonElement element : entries.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Relation entry must be an object");
                }
                JsonObject entry = element.getAsJsonObject();
                JsonElement uuid = entry.get("uuid");
                if (uuid == null) {
                    throw new IllegalArgumentException("Relation uuid is missing");
                }
                UUID id = uuid.isJsonNull() ? null : UUID.fromString(string(entry, "uuid"));
                Relation relation = Relation.valueOf(string(entry, "relation"));
                String name = string(entry, "name");
                if (id == null ? !pendingNames.add(name.toLowerCase(Locale.ROOT)) : !ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate relation identity: " + name);
                }
                loaded.add(new RelationEntry(id, name, relation));
            }
            return loaded;
        }
    }

    public void write(Collection<RelationEntry> entries) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(SCHEMA_VERSION));
        JsonArray values = new JsonArray();
        for (RelationEntry entry : entries) {
            JsonObject value = new JsonObject();
            if (entry.playerId() == null) {
                value.add("uuid", JsonNull.INSTANCE);
            } else {
                value.addProperty("uuid", entry.playerId().toString());
            }
            value.addProperty("name", entry.name());
            value.addProperty("relation", entry.relation().name());
            values.add(value);
        }
        root.add("entries", values);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile());
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.write(gson.toJson(root));
                writer.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Relation " + key + " must be a string");
        }
        return value.getAsString();
    }
}
