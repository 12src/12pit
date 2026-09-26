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
package pit12.feature.online;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import pit12.feature.online.api.Online;

public final class OnlineSettings implements Online {
    private static final String GLOBAL_URL = "https://12pit.askll.dev";
    private final Path path;
    private volatile String provider = PROVIDER_SELF_HOSTED;
    private volatile String region = REGION_GLOBAL;
    private volatile String baseUrl = "";
    private volatile String nickname = "";

    public OnlineSettings(Path path) {
        this.path = path;
    }

    public void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonElement value = root.get("online");
            if (value == null || !value.isJsonObject()) {
                return;
            }
            JsonObject online = value.getAsJsonObject();
            String loadedProvider = string(online, "provider");
            String loadedRegion = string(online, "region");
            if (!loadedProvider.isEmpty() && !PROVIDER_12PIT.equals(loadedProvider)
                    && !PROVIDER_SELF_HOSTED.equals(loadedProvider)) {
                throw new IllegalArgumentException("Unknown online provider");
            }
            if (!loadedRegion.isEmpty() && !REGION_GLOBAL.equals(loadedRegion)) {
                throw new IllegalArgumentException("Unknown online region");
            }
            provider =
                    PROVIDER_12PIT.equals(loadedProvider) ? PROVIDER_12PIT : PROVIDER_SELF_HOSTED;
            region = REGION_GLOBAL;
            baseUrl = string(online, "baseUrl");
            nickname = string(online, "nickname");
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to read online settings", failure);
        }
    }

    public String provider() {
        return provider;
    }

    public String region() {
        return region;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String endpoint() {
        return PROVIDER_SELF_HOSTED.equals(provider) ? baseUrl : GLOBAL_URL;
    }

    public String nickname() {
        return nickname;
    }

    public void setProvider(String value) throws IOException {
        save(value, region, baseUrl, nickname);
        provider = value;
    }

    public void setRegion(String value) throws IOException {
        save(provider, value, baseUrl, nickname);
        region = value;
    }

    public void setSelfHostedUrl(String value) throws IOException {
        save(provider, region, value, nickname);
        baseUrl = value;
    }

    public void setNickname(String value) throws IOException {
        save(provider, region, baseUrl, value);
        nickname = value;
    }

    private void save(String provider, String region, String baseUrl, String nickname)
            throws IOException {
        JsonObject root = new JsonObject();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = new JsonParser().parse(reader).getAsJsonObject();
            }
        }
        JsonObject online = root.has("online") && root.get("online").isJsonObject()
                ? root.getAsJsonObject("online")
                : new JsonObject();
        online.addProperty("provider", provider);
        online.addProperty("region", region);
        online.addProperty("baseUrl", baseUrl);
        online.addProperty("nickname", nickname);
        root.add("online", online);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
