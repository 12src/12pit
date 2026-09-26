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
package pit12.feature.sync;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

final class SyncConfig {
    private volatile String channelId = "";
    private volatile String privateKey = "";
    private volatile String publicKey = "";
    private volatile boolean syncProfiles = true;
    private volatile boolean syncRelations;
    private volatile String profilesUpload = "none";
    private volatile boolean relationsUpload;
    private JsonObject inviteTokens = new JsonObject();

    static SyncConfig load(Path path) {
        SyncConfig config = new SyncConfig();
        if (!Files.exists(path)) {
            return config;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Sync configuration must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            config.channelId = optionalString(root, "channelId");
            config.privateKey = optionalString(root, "privateKey");
            config.publicKey = optionalString(root, "publicKey");
            JsonElement syncProfiles = root.get("syncProfiles");
            config.syncProfiles = syncProfiles == null || syncProfiles.getAsBoolean();
            JsonElement syncRelations = root.get("syncRelations");
            config.syncRelations = syncRelations != null && syncRelations.getAsBoolean();
            String profilesUpload = optionalString(root, "profilesUpload");
            if (!profilesUpload.isEmpty())
                config.profilesUpload = profilesUpload;
            JsonElement relationsUpload = root.get("relationsUpload");
            config.relationsUpload = relationsUpload != null && relationsUpload.getAsBoolean();
            JsonElement inviteTokens = root.get("inviteTokens");
            if (inviteTokens != null && inviteTokens.isJsonObject()) {
                config.inviteTokens = inviteTokens.getAsJsonObject();
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to read sync configuration", failure);
        }
        return config;
    }

    synchronized void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("channelId", channelId);
        root.addProperty("privateKey", privateKey);
        root.addProperty("publicKey", publicKey);
        root.addProperty("syncProfiles", syncProfiles);
        root.addProperty("syncRelations", syncRelations);
        root.addProperty("profilesUpload", profilesUpload);
        root.addProperty("relationsUpload", relationsUpload);
        root.add("inviteTokens", inviteTokens);
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

    String channelId() {
        return channelId;
    }

    void channelId(String channelId) {
        this.channelId = channelId;
    }

    String privateKey() {
        return privateKey;
    }

    void privateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    String publicKey() {
        return publicKey;
    }

    void publicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    boolean syncProfiles() {
        return syncProfiles;
    }

    void syncProfiles(boolean value) {
        syncProfiles = value;
    }

    boolean syncRelations() {
        return syncRelations;
    }

    void syncRelations(boolean value) {
        syncRelations = value;
    }

    String profilesUpload() {
        return profilesUpload;
    }

    void profilesUpload(String value) {
        profilesUpload = value;
    }

    boolean relationsUpload() {
        return relationsUpload;
    }

    void relationsUpload(boolean value) {
        relationsUpload = value;
    }

    synchronized String inviteToken(String id) {
        JsonElement token = inviteTokens.get(id);
        return token != null && token.isJsonPrimitive() ? token.getAsString() : "";
    }

    synchronized void inviteToken(String id, String token) {
        inviteTokens.addProperty(id, token);
    }

    synchronized boolean retainInviteTokens(JsonArray invites) {
        JsonObject active = new JsonObject();
        for (JsonElement entry : invites) {
            String id = entry.getAsJsonObject().get("id").getAsString();
            if (inviteTokens.has(id))
                active.add(id, inviteTokens.get(id));
        }
        boolean changed = active.entrySet().size() != inviteTokens.entrySet().size();
        inviteTokens = active;
        return changed;
    }

    synchronized void clearInviteTokens() {
        inviteTokens = new JsonObject();
    }

    private static String optionalString(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
