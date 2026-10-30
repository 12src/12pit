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
package pit12.feature.denick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Only the serial query worker uses this. */
final class PitPalClient {
    private long nextRequest;

    Map<String, String> owners(String query, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        JsonObject response = request("/api/listings/items/" + query, null, cancelled);
        JsonElement success = response.get("success");
        if (success == null) {
            // THIS IS INTENDED!
            success = response.get("sucess");
        }
        JsonElement data = response.get("data");
        if (success == null || !success.isJsonPrimitive()
                || !success.getAsJsonPrimitive().isBoolean() || !success.getAsBoolean()
                || data == null || !data.isJsonArray()) {
            throw new IOException("PitPal returned an invalid item result");
        }
        Map<String, String> owners = new LinkedHashMap<>();
        for (JsonElement element : data.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IOException("PitPal returned an invalid item");
            }
            JsonElement owner = element.getAsJsonObject().get("ownerid");
            if (owner == null || !owner.isJsonPrimitive() || !owner.getAsJsonPrimitive().isString()
                    || !owner.getAsString().matches("[0-9a-fA-F]{1,32}")) {
                throw new IOException("PitPal returned an invalid owner");
            }
            JsonElement displayName = element.getAsJsonObject().get("ownerusername");
            if (displayName == null || !displayName.isJsonPrimitive()
                    || !displayName.getAsJsonPrimitive().isString()
                    || displayName.getAsString().trim().isEmpty()) {
                throw new IOException("PitPal returned an invalid owner name");
            }
            owners.putIfAbsent(owner.getAsString(), displayName.getAsString());
        }
        return owners;
    }

    String displayName(String owner, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(new JsonPrimitive(owner));
        body.add("uuids", ids);
        JsonObject response = request("/api/ign/usernames", body.toString(), cancelled);
        JsonElement data = response.get("data");
        JsonElement profile =
                data == null || !data.isJsonObject() ? null : data.getAsJsonObject().get(owner);
        if (profile == null || !profile.isJsonObject()) {
            throw new IOException("PitPal could not resolve the owner's name");
        }
        JsonElement level = profile.getAsJsonObject().get("level");
        JsonElement name = profile.getAsJsonObject().get("displayName");
        if (level == null || !level.isJsonPrimitive() || !level.getAsJsonPrimitive().isString()
                || name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                || name.getAsString().trim().isEmpty()) {
            throw new IOException("PitPal returned an invalid owner name");
        }
        String displayName = name.getAsString().replaceFirst("\\[[^]]*] *", "");
        return level.getAsString().isEmpty() ? displayName
                : level.getAsString() + " " + displayName;
    }

    private JsonObject request(String path, String body, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        while (System.currentTimeMillis() < nextRequest) {
            if (cancelled.getAsBoolean()) {
                throw new InterruptedException("Denick query was cancelled");
            }
            // noinspection BusyWait
            Thread.sleep(Math.max(1L, Math.min(100L, nextRequest - System.currentTimeMillis())));
        }
        if (cancelled.getAsBoolean()) {
            throw new InterruptedException("Denick query was cancelled");
        }
        HttpURLConnection connection =
                (HttpURLConnection) new URL("https://pitpal.rocks" + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (body != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
        }
        // Grab these now. We can't read them once the connection opens.
        Map<String, List<String>> requestHeaders = connection.getRequestProperties();
        int status = -1;
        try {
            if (body != null) {
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            status = connection.getResponseCode();
            nextRequest = System.currentTimeMillis() + 650L;
            String remaining = connection.getHeaderField("RateLimit-Remaining");
            String reset = connection.getHeaderField("RateLimit-Reset");
            if (remaining != null && remaining.matches("[0-9]{1,8}") && reset != null
                    && reset.matches("[0-9]{1,8}") && Long.parseLong(remaining) > 0) {
                nextRequest = Math.max(nextRequest, System.currentTimeMillis()
                        + Long.parseLong(reset) * 1000L / Long.parseLong(remaining) + 50L);
            }
            if ("0".equals(connection.getHeaderField("RateLimit-Remaining"))) {
                delay(connection.getHeaderField("RateLimit-Reset"));
            }
            if (status == 429) {
                nextRequest = Math.max(nextRequest, System.currentTimeMillis() + 60000L);
                delay(connection.getHeaderField("Retry-After"));
                nextRequest =
                        Math.max(nextRequest, connection.getHeaderFieldDate("Retry-After", 0));
            }
            if (status != 200) {
                throw new IOException("PitPal returned HTTP " + status);
            }
            try (InputStreamReader reader =
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (!parsed.isJsonObject()) {
                    throw new IOException("PitPal returned invalid JSON");
                }
                return parsed.getAsJsonObject();
            }
        } catch (IOException | RuntimeException failure) {
            throw new IOException("PitPal request failed: " + connection.getRequestMethod() + " "
                    + connection.getURL() + "\nHTTP status: "
                    + (status == -1 ? "No response" : status) + "\nConfigured request headers: "
                    + requestHeaders + "\nResponse headers: "
                    + (status == -1 ? "No response" : connection.getHeaderFields()) + "\nReason: "
                    + failure, failure);
        } finally {
            nextRequest = Math.max(nextRequest, System.currentTimeMillis() + 650L);
            connection.disconnect();
        }
    }

    private void delay(String seconds) {
        if (seconds != null && seconds.matches("[0-9]{1,8}")) {
            nextRequest = Math.max(nextRequest,
                    System.currentTimeMillis() + Long.parseLong(seconds) * 1000L);
        }
    }
}
