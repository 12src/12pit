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
package pit12.feature.relation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class MojangProfileLookup {
    static final class Profile {
        final UUID id;
        final String name;

        Profile(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    Profile lookup(String name) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/"
                        + URLEncoder.encode(name, "UTF-8"))
                .openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 404 || status == 204) {
                return null;
            }
            if (status != 200) {
                throw new IOException("Mojang profile lookup returned HTTP " + status);
            }
            try (InputStreamReader reader =
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    throw new IOException("Mojang profile lookup returned invalid JSON");
                }
                JsonObject object = parsed.getAsJsonObject();
                JsonElement id = object.get("id");
                JsonElement actualName = object.get("name");
                if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()
                        || actualName == null || !actualName.isJsonPrimitive()
                        || !actualName.getAsJsonPrimitive().isString()) {
                    throw new IOException("Mojang profile lookup returned an invalid profile");
                }
                String compact = id.getAsString();
                String resolvedName = actualName.getAsString();
                if (!compact.matches("[0-9a-fA-F]{32}") || resolvedName.isEmpty()
                        || resolvedName.length() > 48 || !resolvedName.equalsIgnoreCase(name)) {
                    throw new IOException("Mojang profile lookup returned an unexpected identity");
                }
                String formatted = compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                        + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-"
                        + compact.substring(20);
                return new Profile(UUID.fromString(formatted), resolvedName);
            }
        } catch (RuntimeException failure) {
            throw new IOException("Mojang profile lookup could not be read", failure);
        } finally {
            connection.disconnect();
        }
    }

    Profile lookup(UUID id) throws IOException {
        if (id == null) {
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://sessionserver.mojang.com/session/minecraft/profile/"
                        + id.toString().replace("-", ""))
                .openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 404 || status == 204) {
                return null;
            }
            if (status != 200) {
                throw new IOException("Mojang profile lookup returned HTTP " + status);
            }
            try (InputStreamReader reader =
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    throw new IOException("Mojang profile lookup returned invalid JSON");
                }
                JsonObject object = parsed.getAsJsonObject();
                JsonElement actualId = object.get("id");
                JsonElement actualName = object.get("name");
                if (actualId == null || !actualId.isJsonPrimitive()
                        || !actualId.getAsJsonPrimitive().isString() || actualName == null
                        || !actualName.isJsonPrimitive()
                        || !actualName.getAsJsonPrimitive().isString()) {
                    throw new IOException("Mojang profile lookup returned an invalid profile");
                }
                String compact = actualId.getAsString();
                String resolvedName = actualName.getAsString();
                if (!compact.matches("[0-9a-fA-F]{32}") || resolvedName.isEmpty()
                        || resolvedName.length() > 48
                        || !id.toString().replace("-", "").equalsIgnoreCase(compact)) {
                    throw new IOException("Mojang profile lookup returned an unexpected identity");
                }
                return new Profile(id, resolvedName);
            }
        } catch (RuntimeException failure) {
            throw new IOException("Mojang profile lookup could not be read", failure);
        } finally {
            connection.disconnect();
        }
    }
}
