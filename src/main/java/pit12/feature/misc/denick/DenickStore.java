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
package pit12.feature.misc.denick;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import pit12.shared.storage.AtomicFile;

final class DenickStore {
    private final Path path;

    DenickStore(Path path) {
        this.path = path;
    }

    Map<String, String> load() throws IOException {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (Files.notExists(path)) {
            return mappings;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("Denick cache must be an object");
            }
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!entry.getKey().matches("[A-Za-z0-9_]{1,16}") || !value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isString()
                        || !value.getAsString().matches("[0-9a-fA-F]{1,32}")) {
                    throw new IOException("Denick cache contains an invalid identity");
                }
                String name = entry.getKey().toLowerCase(Locale.ROOT);
                if (mappings.put(name, value.getAsString()) != null) {
                    throw new IOException("Denick cache contains a duplicate name");
                }
            }
        }
        return mappings;
    }

    void write(Map<String, String> mappings) throws IOException {
        JsonObject data = new JsonObject();
        mappings.forEach(data::addProperty);
        AtomicFile.write(path, new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
}
