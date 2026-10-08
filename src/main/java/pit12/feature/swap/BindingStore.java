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
package pit12.feature.swap;

import static pit12.runtime.languages.Languages.source;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.input.Keyboard;
import pit12.shared.storage.AtomicFile;

final class BindingStore {
    private final Path path;

    BindingStore(Path path) {
        this.path = path;
    }

    List<SwapBinding> load() throws IOException {
        if (Files.notExists(path))
            return new ArrayList<>();
        return decode(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    void write(List<SwapBinding> bindings) throws IOException {
        AtomicFile.write(path, encode(bindings));
    }

    static String encode(Collection<SwapBinding> bindings) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonArray entries = new JsonArray();
        for (SwapBinding binding : bindings) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", binding.key);
            entry.addProperty("type", binding.equipment ? "EQUIPMENT" : "SLOT");
            entry.addProperty("target", binding.target);
            entry.addProperty("name", binding.name);
            entry.addProperty("details", binding.details);
            JsonObject identity = new JsonObject();
            identity.addProperty("item", binding.identity.item);
            identity.addProperty("variant", binding.identity.variant);
            identity.addProperty("kind", binding.identity.kind.name());
            identity.addProperty("value", binding.identity.value);
            entry.add("identity", identity);
            entries.add(entry);
        }
        root.add("bindings", entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    static List<SwapBinding> decode(String text) {
        JsonElement parsed = new JsonParser().parse(text);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(source("Swap bindings must be an object"));
        }
        JsonObject root = parsed.getAsJsonObject();
        if (integer(root, "schemaVersion") != 1) {
            throw new IllegalArgumentException(source("Unsupported swap binding version"));
        }
        JsonElement entries = root.get("bindings");
        if (entries == null || !entries.isJsonArray()) {
            throw new IllegalArgumentException(source("Expected swap binding list"));
        }
        List<SwapBinding> result = new ArrayList<>();
        Set<ItemIdentity> identities = new HashSet<>();
        for (JsonElement value : entries.getAsJsonArray()) {
            if (!value.isJsonObject())
                throw new IllegalArgumentException(source("Invalid swap binding"));
            JsonObject entry = value.getAsJsonObject();
            String type = string(entry, "type");
            if (!"EQUIPMENT".equals(type) && !"SLOT".equals(type)) {
                throw new IllegalArgumentException(source("Unknown swap binding type"));
            }
            JsonElement identityValue = entry.get("identity");
            if (identityValue == null || !identityValue.isJsonObject()) {
                throw new IllegalArgumentException(source("Expected item identity"));
            }
            JsonObject identityData = identityValue.getAsJsonObject();
            String item = string(identityData, "item");
            int variant = integer(identityData, "variant");
            ItemIdentity.Kind kind = ItemIdentity.Kind.valueOf(string(identityData, "kind"));
            String signature = string(identityData, "value");
            if (item.isEmpty() || kind != ItemIdentity.Kind.NAME && signature.isEmpty()
                    || variant < 0
                    || kind == ItemIdentity.Kind.NONCE && Long.parseLong(signature) < 10) {
                throw new IllegalArgumentException(source("Invalid item identity"));
            }
            ItemIdentity identity = new ItemIdentity(item, variant, kind, signature);
            if (!identities.add(identity))
                throw new IllegalArgumentException(source("Duplicate bound item"));
            int key = integer(entry, "key");
            boolean equipment = "EQUIPMENT".equals(type);
            int target = integer(entry, "target");
            String name = string(entry, "name");
            if (key <= 0 || key >= Keyboard.KEYBOARD_SIZE || Keyboard.getKeyName(key) == null
                    || target < 1 || target > (equipment ? 4 : 9) || name.isEmpty()) {
                throw new IllegalArgumentException(source("Invalid swap binding"));
            }
            result.add(new SwapBinding(key, identity, equipment, target, name,
                    string(entry, "details")));
        }
        return result;
    }

    private static String string(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected " + key + " text");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected " + key + " integer");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid " + key + " integer", failure);
        }
    }
}
