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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;
import pit12.feature.Feature;
import pit12.feature.online.api.Online;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.api.Profiles;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.Relations;
import pit12.feature.sync.api.Sync;

public final class SyncFeature implements Feature, Sync, CloudflareSyncClient.Listener {
    private static final Logger LOGGER = Logger.getLogger(SyncFeature.class.getName());
    private static final long RETRY_DELAY_SECONDS = 5L;
    private final Profiles profiles;
    private final Relations relations;
    private final Path configPath;
    private final Online online;
    private ScheduledExecutorService network;
    private final Runnable profileListener = this::uploadProfiles;
    private final Runnable relationListener = this::relationsChanged;
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private volatile SyncConfig config;
    private volatile SyncIdentity identity;
    private volatile CloudflareSyncClient client;
    private volatile String status = "unconfigured";
    private volatile String message = null;
    private volatile JsonArray invites = new JsonArray();
    private volatile String joinMode = "open";
    private volatile JsonArray members = new JsonArray();
    private volatile JsonArray remoteProfiles = new JsonArray();
    private volatile JsonArray latestRelations = new JsonArray();
    private volatile SyncConfig pendingConfig;
    private volatile SyncIdentity pendingIdentity;
    private volatile boolean writeProfiles;
    private volatile boolean writeRelations;
    private volatile boolean initialSnapshotApplied;
    private volatile boolean relationSyncPending;
    private final AtomicBoolean joining = new AtomicBoolean();
    private final AtomicBoolean keyChanging = new AtomicBoolean();
    private final Map<String, RelationEntry> knownRelations =
            new LinkedHashMap<String, RelationEntry>();
    private final AtomicInteger identityRefreshes = new AtomicInteger();
    private boolean applyingRemote;
    private boolean started;

    public SyncFeature(Profiles profiles, Relations relations, Online online, Path directory) {
        this.profiles = profiles;
        this.relations = relations;
        this.online = online;
        configPath = directory.resolve("sync.json");
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        config = SyncConfig.load(configPath);
        online.load();
        network = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "12pit-sync");
            thread.setDaemon(true);
            return thread;
        });
        try {
            identity = SyncIdentity.loadOrCreate(config);
            saveConfig();
        } catch (GeneralSecurityException failure) {
            status = "error";
            message = "Unable to initialize sync identity";
            LOGGER.log(Level.WARNING, "Unable to initialize sync identity", failure);
        }
        started = true;
        profiles.addListener(profileListener);
        relations.addChangeListener(relationListener);
        if (configured()) {
            status = "connecting";
            network.execute(this::connectSocket);
        } else {
            status = "unconfigured";
        }
        notifyListeners();
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        profiles.removeListener(profileListener);
        relations.removeChangeListener(relationListener);
        closeSocket();
        if (network != null) {
            network.shutdownNow();
            network = null;
        }
        notifyListeners();
    }

    @Override
    public synchronized void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public JsonObject state() {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", message);
        result.addProperty("channelId", config == null ? "" : config.channelId());
        result.addProperty("joinMode", joinMode);
        result.addProperty("fingerprint", identity == null ? "" : identity.fingerprint());
        result.addProperty("syncProfiles", config != null && config.syncProfiles());
        result.addProperty("syncRelations", config != null && config.syncRelations());
        result.addProperty("relationsReadOnly", relationsReadOnly());
        result.addProperty("profilesUpload", config == null ? "none" : config.profilesUpload());
        result.addProperty("relationsUpload", config != null && config.relationsUpload());
        result.add("members", copy(members));
        result.add("remoteProfiles", copy(remoteProfiles));
        result.add("invites", copy(invites));
        return result;
    }

    @Override
    public JsonObject onlineState() {
        JsonObject result = new JsonObject();
        result.addProperty("provider", online.provider());
        result.addProperty("region", online.region());
        result.addProperty("baseUrl", online.baseUrl());
        result.addProperty("endpoint", online.endpoint());
        result.addProperty("nickname", online.nickname());
        return result;
    }

    @Override
    public void createChannel() {
        requireStarted();
        requireNoChannel();
        if (!joining.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Channel request is already in progress");
        }
        status = "joining";
        notifyListeners();
        network.execute(() -> {
            try {
                String endpoint = prepareEndpoint();
                JsonObject body = new JsonObject();
                body.addProperty("publicKey", identity.publicKey());
                JsonObject result = post(endpoint, "/api/channels", body);
                configureChannel(result);
            } catch (Exception failure) {
                fail(failure);
            } finally {
                joining.set(false);
            }
        });
    }

    @Override
    public void joinChannel(String channelId, String inviteToken, String password) {
        requireStarted();
        requireNoChannel();
        UUID.fromString(channelId);
        if (!joining.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Channel request is already in progress");
        }
        status = "joining";
        notifyListeners();
        network.execute(() -> {
            try {
                String endpoint = prepareEndpoint();
                JsonObject body = new JsonObject();
                if (inviteToken != null && !inviteToken.isEmpty())
                    body.addProperty("token", inviteToken);
                if (password != null && !password.isEmpty())
                    body.addProperty("password", password);
                body.addProperty("publicKey", identity.publicKey());
                JsonObject result = post(endpoint,
                        "/api/channels/" + UUID.fromString(channelId) + "/join", body);
                configureChannel(result);
            } catch (Exception failure) {
                fail(failure);
            } finally {
                joining.set(false);
            }
        });
    }

    @Override
    public void setProvider(String provider) {
        requireStarted();
        if (!Online.PROVIDER_12PIT.equals(provider)
                && !Online.PROVIDER_SELF_HOSTED.equals(provider)) {
            throw new IllegalArgumentException("Unknown online provider");
        }
        if (provider.equals(online.provider())) {
            return;
        }
        requireNoChannel();
        try {
            online.setProvider(provider);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to save online settings", failure);
        }
        status = "unconfigured";
        notifyListeners();
    }

    @Override
    public void setRegion(String region) {
        requireStarted();
        if (!Online.REGION_GLOBAL.equals(region)) {
            throw new IllegalArgumentException("Unknown online region");
        }
        if (region.equals(online.region())) {
            return;
        }
        requireNoChannel();
        try {
            online.setRegion(region);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to save online settings", failure);
        }
        status = "unconfigured";
        notifyListeners();
    }

    @Override
    public void setSelfHostedUrl(String url) {
        requireStarted();
        if (!Online.PROVIDER_SELF_HOSTED.equals(online.provider())) {
            throw new IllegalArgumentException("Self-hosted URL is only available for Self-hosted");
        }
        String value = url == null ? "" : url.trim();
        if (!value.isEmpty()) {
            validateEndpoint(value);
            value = normalizeEndpoint(value);
        }
        if (value.equals(online.baseUrl())) {
            return;
        }
        requireNoChannel();
        try {
            online.setSelfHostedUrl(value);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to save online settings", failure);
        }
        status = "unconfigured";
        notifyListeners();
    }

    @Override
    public void setNickname(String nickname) {
        requireStarted();
        String value = nickname == null ? "" : nickname.trim();
        if (value.length() > 32 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Nickname must be at most 32 characters without control characters");
        }
        if (value.equals(online.nickname()))
            return;
        try {
            online.setNickname(value);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to save online settings", failure);
        }
        sendNickname();
        notifyListeners();
    }

    @Override
    public void regenerateKey() {
        requireStarted();
        changeIdentity("");
    }

    @Override
    public void importKey(String privateKey) {
        requireStarted();
        String value = requireText(privateKey, "Private key is required");
        if (!value.contains("BEGIN PGP PRIVATE KEY BLOCK")) {
            throw new IllegalArgumentException("Expected an armored OpenPGP private key");
        }
        changeIdentity(value);
    }

    private void changeIdentity(String privateKey) {
        if (joining.get())
            throw new IllegalArgumentException("Channel request is in progress");
        if (!config.channelId().isEmpty())
            requireSocket();
        if (!keyChanging.compareAndSet(false, true)) {
            throw new IllegalArgumentException("Key change is already in progress");
        }
        String previousChannel = config.channelId();
        status = "preparingKey";
        notifyListeners();
        network.execute(() -> {
            try {
                SyncConfig next = new SyncConfig();
                if (!privateKey.isEmpty()) {
                    next.privateKey(Base64.getEncoder()
                            .encodeToString(privateKey.getBytes(StandardCharsets.UTF_8)));
                }
                SyncIdentity replacement = SyncIdentity.loadOrCreate(next);
                if (!previousChannel.equals(config.channelId())) {
                    keyChanging.set(false);
                    return;
                }
                if (config.channelId().isEmpty()) {
                    applyIdentity(next, replacement);
                    return;
                }
                CloudflareSyncClient active = requireSocket();
                pendingConfig = next;
                pendingIdentity = replacement;
                status = "leaving";
                notifyListeners();
                JsonObject command = new JsonObject();
                command.addProperty("type", "leave");
                active.command(command);
            } catch (GeneralSecurityException | RuntimeException failure) {
                keyChanging.set(false);
                message = failure.getMessage() == null ? "Unable to change key"
                        : failure.getMessage();
                status = isConnected() ? "connected" : configured() ? "connecting" : "unconfigured";
                notifyListeners();
                LOGGER.log(Level.WARNING, "Unable to change sync key", failure);
            }
        });
    }

    private void applyIdentity(SyncConfig next, SyncIdentity replacement) {
        config.privateKey(next.privateKey());
        config.publicKey(next.publicKey());
        identity = replacement;
        saveConfig();
        keyChanging.set(false);
        message = null;
        status = "unconfigured";
        notifyListeners();
    }

    @Override
    public void requestInvite() {
        CloudflareSyncClient active = client;
        if (active == null || !active.isOpen()) {
            throw new IllegalArgumentException("Sync is not connected");
        }
        JsonObject command = new JsonObject();
        command.addProperty("type", "invite");
        active.command(command);
    }

    @Override
    public void setJoinPolicy(String mode, String password) {
        if (!"open".equals(mode) && !"password".equals(mode) && !"invite".equals(mode)) {
            throw new IllegalArgumentException("Invalid join mode");
        }
        if ("password".equals(mode) && (password == null || password.isEmpty())) {
            throw new IllegalArgumentException("Password is required");
        }
        JsonObject command = new JsonObject();
        command.addProperty("type", "joinPolicy");
        command.addProperty("mode", mode);
        if ("password".equals(mode))
            command.addProperty("password", password);
        requireSocket().command(command);
    }

    @Override
    public void revokeInvite(String inviteId) {
        if (inviteId == null || !inviteId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid invitation");
        }
        JsonObject command = new JsonObject();
        command.addProperty("type", "revokeInvite");
        command.addProperty("id", inviteId);
        requireSocket().command(command);
    }

    @Override
    public void leaveChannel() {
        requireStarted();
        CloudflareSyncClient active = requireSocket();
        JsonObject command = new JsonObject();
        command.addProperty("type", "leave");
        status = "leaving";
        notifyListeners();
        active.command(command);
    }

    @Override
    public void dissolveChannel() {
        JsonObject command = new JsonObject();
        command.addProperty("type", "dissolve");
        CloudflareSyncClient active = requireSocket();
        status = "dissolving";
        notifyListeners();
        active.command(command);
    }

    @Override
    public void refreshProfiles() {
        JsonObject command = new JsonObject();
        command.addProperty("type", "listProfiles");
        requireSocket().command(command);
    }

    @Override
    public void importProfile(String ownerId, String profileId) {
        JsonObject command = new JsonObject();
        command.addProperty("type", "getProfile");
        command.addProperty("ownerId", requireText(ownerId, "Owner is required"));
        command.addProperty("profileId", UUID.fromString(profileId).toString());
        requireSocket().command(command);
    }

    @Override
    public void setUploads(String profilesUpload, boolean relationsUpload) {
        requireStarted();
        if (!"none".equals(profilesUpload) && !"current".equals(profilesUpload)
                && !"all".equals(profilesUpload)) {
            throw new IllegalArgumentException("Unknown profile upload mode");
        }
        boolean clearProfiles =
                !"none".equals(config.profilesUpload()) && "none".equals(profilesUpload);
        boolean shareRelations = !config.relationsUpload() && relationsUpload;
        boolean clearRelations = config.relationsUpload() && !relationsUpload;
        boolean enableRelationSync = relationsUpload && !config.syncRelations();
        config.profilesUpload(profilesUpload);
        config.relationsUpload(relationsUpload);
        if (enableRelationSync) {
            config.syncRelations(true);
        }
        saveConfig();
        if (clearProfiles && isConnected()) {
            JsonObject command = new JsonObject();
            command.addProperty("type", "clearProfiles");
            client.command(command);
        } else {
            uploadProfiles();
        }
        if (clearRelations && isConnected()) {
            JsonObject command = new JsonObject();
            command.addProperty("type", "clearRelations");
            client.command(command);
        } else if (shareRelations && initialSnapshotApplied) {
            JsonArray current = copy(latestRelations);
            runOnClient(() -> {
                if (config.relationsUpload()) {
                    applyRelations(current, true);
                    publishRelations();
                }
            });
        } else if (shareRelations) {
            publishRelations();
        } else {
            uploadRelations();
        }
        notifyListeners();
    }

    @Override
    public void setSyncSelection(boolean profilesSync, boolean relationsSync) {
        requireStarted();
        config.syncProfiles(profilesSync);
        config.syncRelations(relationsSync);
        if (!relationsSync)
            relationSyncPending = false;
        saveConfig();
        if (!profilesSync)
            remoteProfiles = new JsonArray();
        else if (isConnected())
            refreshProfiles();
        if (relationsSync && initialSnapshotApplied) {
            JsonArray current = copy(latestRelations);
            runOnClient(() -> {
                if (config.syncRelations())
                    applyRelations(current, config.relationsUpload());
            });
        }
        notifyListeners();
    }

    @Override
    public boolean relationsReadOnly() {
        return config != null && !config.channelId().isEmpty() && config.syncRelations()
                && !writeRelations;
    }

    @Override
    public void setAccess(String memberId, String role, boolean profilesWrite,
            boolean relationsWrite) {
        if (!"owner".equals(role) && !"writer".equals(role) && !"reader".equals(role)) {
            throw new IllegalArgumentException("Unknown member role");
        }
        CloudflareSyncClient active = requireSocket();
        JsonObject command = new JsonObject();
        command.addProperty("type", "access");
        command.addProperty("memberId", requireText(memberId, "Member ID is required"));
        command.addProperty("role", role);
        command.addProperty("writeProfiles", profilesWrite);
        command.addProperty("writeRelations", relationsWrite);
        active.command(command);
    }

    @Override
    public void removeMember(String memberId) {
        JsonObject command = new JsonObject();
        command.addProperty("type", "removeMember");
        command.addProperty("memberId", requireText(memberId, "Member ID is required"));
        requireSocket().command(command);
    }

    @Override
    public void onSnapshot(CloudflareSyncClient source, JsonObject snapshot) {
        if (source != client) {
            return;
        }
        joinMode = snapshot.has("joinMode") ? snapshot.get("joinMode").getAsString() : "open";
        JsonArray listed =
                snapshot.has("invites") ? requiredArray(snapshot, "invites") : new JsonArray();
        JsonArray visible = new JsonArray();
        for (JsonElement element : listed) {
            JsonObject invite = element.getAsJsonObject();
            JsonObject entry = new JsonObject();
            entry.addProperty("id", invite.get("id").getAsString());
            entry.addProperty("expiresAt", invite.get("expiresAt").getAsLong());
            String token = config.inviteToken(entry.get("id").getAsString());
            if (!token.isEmpty())
                entry.addProperty("token", token);
            visible.add(entry);
        }
        invites = visible;
        if (config.retainInviteTokens(listed))
            saveConfig();
        updateMembers(snapshot);
        latestRelations = copy(requiredArray(snapshot, "relations"));
        runOnClient(() -> {
            if (source == client)
                applySnapshot(snapshot);
        });
    }

    @Override
    public void onRelationPatch(CloudflareSyncClient source, JsonObject patch) {
        if (source != client || !config.syncRelations()) {
            return;
        }
        String action = patch.get("action").getAsString();
        UUID playerId = optionalUuid(patch);
        String name = patch.get("name").getAsString();
        Relation relation = Relation.valueOf(patch.get("relation").getAsString());
        runOnClient(() -> {
            if (source != client || !config.syncRelations())
                return;
            try {
                updateLatestRelations(action, playerId, name, relation);
                applyingRemote = true;
                relations.applyRemotePatch(action, playerId, name, relation);
                synchronized (knownRelations) {
                    knownRelations.clear();
                    knownRelations.putAll(relationMap());
                }
            } catch (RuntimeException failure) {
                message = failure.getMessage() == null ? "Unable to apply relation update"
                        : failure.getMessage();
                status = "error";
                LOGGER.log(Level.WARNING, "Unable to apply remote relation update", failure);
            } finally {
                applyingRemote = false;
            }
        });
    }

    @Override
    public void onRelationConflicts(CloudflareSyncClient source, JsonArray conflicts) {
        if (source != client || !config.relationsUpload()) {
            return;
        }
        for (JsonElement element : conflicts) {
            if (!element.isJsonObject())
                continue;
            JsonObject conflict = element.getAsJsonObject();
            UUID playerId = optionalUuid(conflict);
            String name = conflict.get("name").getAsString();
            boolean verified = conflict.has("verified") && conflict.get("verified").getAsBoolean();
            if (verified) {
                sendRelationRetry(conflict, true);
                continue;
            }
            boolean lookupByName = "name".equals(conflict.get("kind").getAsString());
            identityRefreshes.incrementAndGet();
            relations.refreshIdentity(playerId, name, lookupByName, refreshed -> {
                identityRefreshes.decrementAndGet();
                if (refreshed == null) {
                    applyRemoteConflict(conflict);
                    return;
                }
                sendRelationRetry(conflict, refreshed, false);
            });
        }
    }

    @Override
    public void onInvite(CloudflareSyncClient source, String id, String token, long expiresAt) {
        if (source == client) {
            config.inviteToken(id, token);
            saveConfig();
        }
    }

    @Override
    public void onReady(CloudflareSyncClient source) {
        if (source != client) {
            return;
        }
        initialSnapshotApplied = false;
        status = "connected";
        message = null;
        sendNickname();
        notifyListeners();
    }

    private void sendNickname() {
        CloudflareSyncClient active = client;
        if (active != null && active.isOpen()) {
            JsonObject command = new JsonObject();
            command.addProperty("type", "nickname");
            command.addProperty("nickname", online.nickname());
            active.command(command);
        }
    }

    @Override
    public void onLeft(CloudflareSyncClient source) {
        if (source == client) {
            SyncConfig next = pendingConfig;
            SyncIdentity replacement = pendingIdentity;
            pendingConfig = null;
            pendingIdentity = null;
            resetChannel();
            if (next != null && replacement != null) {
                network.execute(() -> applyIdentity(next, replacement));
            } else {
                keyChanging.set(false);
            }
        }
    }

    @Override
    public void onDissolved(CloudflareSyncClient source) {
        if (source == client) {
            pendingConfig = null;
            pendingIdentity = null;
            keyChanging.set(false);
            resetChannel();
        }
    }

    @Override
    public void onProfiles(CloudflareSyncClient source, JsonArray entries) {
        if (source == client && config.syncProfiles()) {
            remoteProfiles = copy(entries);
            notifyListeners();
        }
    }

    @Override
    public void onProfile(CloudflareSyncClient source, String data) {
        if (source != client)
            return;
        runOnClient(() -> {
            if (source != client)
                return;
            try {
                profiles.importProfiles(java.util.Collections.singletonList(data));
                message = null;
            } catch (RuntimeException failure) {
                message = failure.getMessage() == null ? "Unable to import profile"
                        : failure.getMessage();
            }
            notifyListeners();
        });
    }

    @Override
    public void onError(CloudflareSyncClient source, String error) {
        if (source == client) {
            message = error;
            if ("leaving".equals(status) || "dissolving".equals(status)) {
                status = "connected";
                pendingConfig = null;
                pendingIdentity = null;
                keyChanging.set(false);
            } else if (!"connected".equals(status)) {
                status = "error";
            }
            notifyListeners();
        }
    }

    @Override
    public void onClosed(CloudflareSyncClient source, String reason) {
        if (source != client) {
            return;
        }
        if ("Left channel".equals(reason)) {
            onLeft(source);
            return;
        }
        if ("Channel dissolved".equals(reason)) {
            onDissolved(source);
            return;
        }
        if ("Membership revoked".equals(reason)) {
            pendingConfig = null;
            pendingIdentity = null;
            keyChanging.set(false);
            resetChannel();
            return;
        }
        client = null;
        status = "connecting";
        message = reason;
        notifyListeners();
        if (started && configured()) {
            network.schedule(this::connectSocket, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void connectSocket() {
        if (!started || !configured() || identity == null) {
            return;
        }
        closeSocket();
        initialSnapshotApplied = false;
        relationSyncPending = false;
        try {
            URI socketUri = socketUri(serviceEndpoint(), config.channelId());
            CloudflareSyncClient next =
                    new CloudflareSyncClient(socketUri, identity, config.channelId(), this);
            client = next;
            status = "connecting";
            next.connect();
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
        }
    }

    private void closeSocket() {
        CloudflareSyncClient active = client;
        client = null;
        if (active != null) {
            active.close();
        }
    }

    private void applySnapshot(JsonObject snapshot) {
        if (config.syncRelations()) {
            applyRelations(requiredArray(snapshot, "relations"), config.relationsUpload());
        } else {
            synchronized (knownRelations) {
                knownRelations.clear();
                knownRelations.putAll(relationMap());
            }
        }
        if (!initialSnapshotApplied) {
            initialSnapshotApplied = true;
            if ("none".equals(config.profilesUpload())) {
                JsonObject command = new JsonObject();
                command.addProperty("type", "clearProfiles");
                client.command(command);
            } else
                uploadProfiles();
            if (!config.relationsUpload()) {
                JsonObject command = new JsonObject();
                command.addProperty("type", "clearRelations");
                client.command(command);
            } else
                publishRelations();
        }
    }

    private void applyRelations(JsonArray source, boolean preferLocal) {
        if (relations.readinessProblem() != null) {
            relationSyncPending = true;
            return;
        }
        boolean applied = false;
        try {
            applyingRemote = true;
            List<RelationEntry> incoming = parseRelations(source);
            List<RelationEntry> entries = preferLocal ? mergeRelations(incoming) : incoming;
            relations.applyRemoteSnapshot(entries);
            synchronized (knownRelations) {
                knownRelations.clear();
                knownRelations.putAll(relationMap());
            }
            message = null;
            relationSyncPending = false;
            applied = true;
        } catch (RuntimeException failure) {
            message = failure.getMessage() == null ? "Unable to apply relations"
                    : failure.getMessage();
            LOGGER.log(Level.WARNING, "Unable to apply synced relations", failure);
        } finally {
            applyingRemote = false;
            notifyListeners();
        }
        if (applied && initialSnapshotApplied && config.relationsUpload()) {
            publishRelations();
        }
    }

    private List<RelationEntry> parseRelations(JsonArray source) {
        List<RelationEntry> entries = new ArrayList<RelationEntry>();
        for (JsonElement element : source) {
            JsonObject relation = element.getAsJsonObject();
            entries.add(
                    new RelationEntry(optionalUuid(relation), relation.get("name").getAsString(),
                            Relation.valueOf(relation.get("relation").getAsString())));
        }
        return entries;
    }

    private List<RelationEntry> mergeRelations(List<RelationEntry> incoming) {
        Map<String, RelationEntry> localByName = relationMap();
        Map<UUID, RelationEntry> localById = new LinkedHashMap<UUID, RelationEntry>();
        for (RelationEntry entry : localByName.values()) {
            if (entry.playerId() != null) {
                localById.put(entry.playerId(), entry);
            }
        }
        Map<String, RelationEntry> known;
        synchronized (knownRelations) {
            known = new LinkedHashMap<String, RelationEntry>(knownRelations);
        }
        Set<String> changedLocalNames = new HashSet<String>();
        for (Map.Entry<String, RelationEntry> entry : localByName.entrySet()) {
            RelationEntry previous = known.get(entry.getKey());
            if (previous == null || !sameRelation(previous, entry.getValue())) {
                changedLocalNames.add(entry.getKey());
            }
        }
        Set<String> replacedLocalNames = new HashSet<String>();
        List<RelationEntry> remoteWinners = new ArrayList<RelationEntry>();
        for (RelationEntry remote : incoming) {
            String nameKey = remote.name().toLowerCase(Locale.ROOT);
            RelationEntry local = localByName.get(nameKey);
            if (local != null) {
                if (!changedLocalNames.contains(nameKey)) {
                    replacedLocalNames.add(nameKey);
                    remoteWinners.add(remote);
                }
                continue;
            }
            RelationEntry sameId =
                    remote.playerId() == null ? null : localById.get(remote.playerId());
            if (sameId != null) {
                String localKey = sameId.name().toLowerCase(Locale.ROOT);
                if (!changedLocalNames.contains(localKey)) {
                    replacedLocalNames.add(localKey);
                    remoteWinners.add(remote);
                }
                continue;
            }
            if (!known.containsKey(nameKey)) {
                remoteWinners.add(remote);
            }
        }
        List<RelationEntry> merged = new ArrayList<RelationEntry>();
        Set<UUID> usedIds = new HashSet<UUID>();
        for (Map.Entry<String, RelationEntry> entry : localByName.entrySet()) {
            if (replacedLocalNames.contains(entry.getKey()))
                continue;
            RelationEntry local = entry.getValue();
            if (local.playerId() == null || usedIds.add(local.playerId())) {
                merged.add(local);
            }
        }
        Set<String> usedNames = new HashSet<String>();
        for (RelationEntry entry : merged) {
            usedNames.add(entry.name().toLowerCase(Locale.ROOT));
        }
        for (RelationEntry remote : remoteWinners) {
            String nameKey = remote.name().toLowerCase(Locale.ROOT);
            if (usedNames.contains(nameKey)
                    || remote.playerId() != null && usedIds.contains(remote.playerId())) {
                continue;
            }
            merged.add(remote);
            usedNames.add(nameKey);
            if (remote.playerId() != null)
                usedIds.add(remote.playerId());
        }
        return merged;
    }

    private void uploadProfiles() {
        if (applyingRemote || !initialSnapshotApplied || !writeProfiles || !isConnected()
                || "none".equals(config.profilesUpload())) {
            return;
        }
        try {
            ProfilesSnapshot snapshot = profiles.snapshot();
            if (snapshot.loadState() == ProfilesSnapshot.LoadState.LOADING)
                return;
            ArrayList<UUID> ids = new ArrayList<UUID>();
            for (ProfileSummary profile : snapshot.profiles()) {
                if ("all".equals(config.profilesUpload())
                        || profile.id().equals(snapshot.activeProfileId())) {
                    ids.add(profile.id());
                }
            }
            JsonArray payload = new JsonArray();
            for (String data : profiles.exportProfiles(ids)) {
                payload.add(new JsonPrimitive(data));
            }
            JsonObject command = new JsonObject();
            command.addProperty("type", "replaceProfiles");
            command.add("profiles", payload);
            client.command(command);
        } catch (RuntimeException failure) {
            message = failure.getMessage();
        }
    }

    private void uploadRelations() {
        if (applyingRemote || !initialSnapshotApplied || !writeRelations || !isConnected()
                || !config.relationsUpload() || relations.readinessProblem() != null
                || identityRefreshes.get() > 0) {
            return;
        }
        Map<String, RelationEntry> current = relationMap();
        Map<String, RelationEntry> previous;
        synchronized (knownRelations) {
            previous = new LinkedHashMap<String, RelationEntry>(knownRelations);
        }
        List<JsonObject> batch = new ArrayList<JsonObject>();
        for (Map.Entry<String, RelationEntry> entry : current.entrySet()) {
            RelationEntry previousEntry = previous.get(entry.getKey());
            if (sameRelation(previousEntry, entry.getValue())) {
                continue;
            }
            batch.add(relationCommand("set", entry.getValue(), false, false));
        }
        for (Map.Entry<String, RelationEntry> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                batch.add(relationCommand("remove", entry.getValue(), false, false));
            }
        }
        sendRelationBatch(batch);
        synchronized (knownRelations) {
            knownRelations.clear();
            knownRelations.putAll(current);
        }
    }

    private void publishRelations() {
        if (!initialSnapshotApplied || !writeRelations || !isConnected()
                || !config.relationsUpload() || relations.readinessProblem() != null
                || identityRefreshes.get() > 0) {
            return;
        }
        Map<String, RelationEntry> remote = relationMap(latestRelations);
        Map<String, RelationEntry> current = relationMap();
        List<JsonObject> batch = new ArrayList<JsonObject>();
        for (Map.Entry<String, RelationEntry> entry : current.entrySet()) {
            if (sameRelation(remote.get(entry.getKey()), entry.getValue())) {
                continue;
            }
            batch.add(relationCommand("set", entry.getValue(), false, false));
        }
        sendRelationBatch(batch);
        synchronized (knownRelations) {
            knownRelations.clear();
            knownRelations.putAll(current);
        }
    }

    private JsonObject relationCommand(String action, RelationEntry entry, boolean force,
            boolean verified) {
        JsonObject command = new JsonObject();
        command.addProperty("action", action);
        command.addProperty("name", entry.name());
        command.addProperty("relation", entry.relation().name());
        command.addProperty("uuid", entry.playerId() == null ? null : entry.playerId().toString());
        if (force)
            command.addProperty("force", true);
        if (verified)
            command.addProperty("verified", true);
        return command;
    }

    private boolean sendRelationBatch(List<JsonObject> entries) {
        if (entries.isEmpty() || !isConnected())
            return false;
        JsonObject command = new JsonObject();
        command.addProperty("type", "relationBatch");
        JsonArray values = new JsonArray();
        for (JsonObject entry : entries)
            values.add(entry);
        command.add("entries", values);
        client.command(command);
        return true;
    }

    private void sendRelationRetry(JsonObject conflict, RelationEntry entry, boolean force) {
        List<JsonObject> batch = new ArrayList<JsonObject>();
        batch.add(relationCommand(conflict.get("action").getAsString(), entry, force, true));
        sendRelationBatch(batch);
    }

    private void sendRelationRetry(JsonObject conflict, boolean force) {
        RelationEntry entry =
                new RelationEntry(optionalUuid(conflict), conflict.get("name").getAsString(),
                        Relation.valueOf(conflict.get("relation").getAsString()));
        sendRelationRetry(conflict, entry, force);
    }

    private void applyRemoteConflict(JsonObject conflict) {
        JsonObject existing = conflict.getAsJsonObject("existing");
        if (existing == null)
            return;
        try {
            applyingRemote = true;
            relations.applyRemotePatch("set", optionalUuid(existing),
                    existing.get("name").getAsString(),
                    Relation.valueOf(existing.get("relation").getAsString()));
            synchronized (knownRelations) {
                knownRelations.clear();
                knownRelations.putAll(relationMap());
            }
        } catch (RuntimeException failure) {
            message = failure.getMessage() == null ? "Unable to apply remote relation"
                    : failure.getMessage();
            LOGGER.log(Level.WARNING, "Unable to apply fallback relation", failure);
        } finally {
            applyingRemote = false;
        }
    }

    private void updateLatestRelations(String action, UUID playerId, String name,
            Relation relation) {
        Map<String, RelationEntry> remote = relationMap(latestRelations);
        String key = name.toLowerCase(Locale.ROOT);
        if ("remove".equals(action)) {
            remote.remove(key);
            if (playerId != null) {
                remote.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId()));
            }
        } else {
            if (playerId != null) {
                remote.entrySet().removeIf(entry -> playerId.equals(entry.getValue().playerId())
                        && !entry.getKey().equals(key));
            }
            remote.put(key, new RelationEntry(playerId, name, relation));
        }
        JsonArray values = new JsonArray();
        for (RelationEntry entry : remote.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("uuid",
                    entry.playerId() == null ? null : entry.playerId().toString());
            value.addProperty("name", entry.name());
            value.addProperty("relation", entry.relation().name());
            values.add(value);
        }
        latestRelations = values;
    }

    private Map<String, RelationEntry> relationMap() {
        Map<String, RelationEntry> result = new LinkedHashMap<String, RelationEntry>();
        for (Relation relation : new Relation[] {Relation.FRIEND, Relation.ENEMY}) {
            for (RelationEntry entry : relations.entries(relation)) {
                result.put(entry.name().toLowerCase(Locale.ROOT), entry);
            }
        }
        return result;
    }

    private Map<String, RelationEntry> relationMap(JsonArray source) {
        Map<String, RelationEntry> result = new LinkedHashMap<String, RelationEntry>();
        for (JsonElement element : source) {
            JsonObject value = element.getAsJsonObject();
            RelationEntry entry =
                    new RelationEntry(optionalUuid(value), value.get("name").getAsString(),
                            Relation.valueOf(value.get("relation").getAsString()));
            result.put(entry.name().toLowerCase(Locale.ROOT), entry);
        }
        return result;
    }

    private boolean sameRelation(RelationEntry left, RelationEntry right) {
        if (left == null || right == null)
            return left == right;
        return left.relation() == right.relation()
                && java.util.Objects.equals(left.playerId(), right.playerId())
                && left.name().equals(right.name());
    }

    private static UUID optionalUuid(JsonObject object) {
        JsonElement value = object.get("uuid");
        return value == null || value.isJsonNull() ? null : UUID.fromString(value.getAsString());
    }

    private void relationsChanged() {
        if (relationSyncPending && config.syncRelations() && initialSnapshotApplied
                && relations.readinessProblem() == null) {
            applyRelations(copy(latestRelations), config.relationsUpload());
        } else {
            uploadRelations();
        }
    }

    private void updateMembers(JsonObject snapshot) {
        JsonArray remoteMembers = requiredArray(snapshot, "members");
        members = copy(remoteMembers);
        JsonArray visibleProfiles = new JsonArray();
        for (JsonElement profile : remoteProfiles) {
            String ownerId = profile.getAsJsonObject().get("ownerId").getAsString();
            for (JsonElement member : remoteMembers) {
                if (ownerId.equals(member.getAsJsonObject().get("id").getAsString())) {
                    visibleProfiles.add(profile);
                    break;
                }
            }
        }
        remoteProfiles = visibleProfiles;
        writeProfiles = false;
        writeRelations = false;
        String currentId = identity.fingerprint();
        for (JsonElement element : remoteMembers) {
            JsonObject member = element.getAsJsonObject();
            if (currentId.equals(member.get("id").getAsString())) {
                writeProfiles = member.get("writeProfiles").getAsBoolean();
                writeRelations = member.get("writeRelations").getAsBoolean();
                break;
            }
        }
        notifyListeners();
    }

    private void configureChannel(JsonObject response) {
        config.channelId(response.get("channelId").getAsString());
        saveConfig();
        status = "connecting";
        message = null;
        notifyListeners();
        network.execute(this::connectSocket);
    }

    private String prepareEndpoint() throws IOException, GeneralSecurityException {
        if (identity == null) {
            identity = SyncIdentity.loadOrCreate(config);
            saveConfig();
        }
        String endpoint = serviceEndpoint();
        URI parsed = URI.create(endpoint);
        if (!"http".equals(parsed.getScheme()) && !"https".equals(parsed.getScheme())) {
            throw new IllegalArgumentException("Endpoint must use http or https");
        }
        return endpoint;
    }

    private JsonObject post(String endpoint, String path, JsonObject body) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(normalizeEndpoint(endpoint) + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(request.length);
        connection.getOutputStream().write(request);
        int statusCode = connection.getResponseCode();
        InputStream input =
                statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = read(input);
        String metadata = responseMetadata(connection);
        JsonObject parsed = parseResponse(response, statusCode, metadata);
        if (statusCode >= 400) {
            throw new IOException("Sync request failed (HTTP " + statusCode + "): "
                    + responseError(parsed) + metadata);
        }
        return parsed;
    }

    private static JsonObject parseResponse(String response, int statusCode, String metadata)
            throws IOException {
        try {
            JsonElement parsed = new JsonParser().parse(response);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Response is not a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException failure) {
            String detail = response == null ? "" : response.trim();
            if (detail.length() > 160) {
                detail = detail.substring(0, 160) + "...";
            }
            throw new IOException("Sync service returned invalid JSON (HTTP " + statusCode + ")"
                    + (detail.isEmpty() ? "" : ": " + detail) + metadata, failure);
        }
    }

    private static String responseMetadata(HttpURLConnection connection) {
        String rayId = connection.getHeaderField("CF-Ray");
        return rayId == null || rayId.trim().isEmpty() ? "" : " [CF-Ray: " + rayId.trim() + "]";
    }

    private static String responseError(JsonObject response) {
        if (response.has("error")) {
            return response.get("error").getAsString();
        }
        if (response.has("detail")) {
            String detail = response.get("detail").getAsString();
            if (response.has("error_code")) {
                return "Cloudflare " + response.get("error_code").getAsString() + ": " + detail;
            }
            return detail;
        }
        if (response.has("message")) {
            return response.get("message").getAsString();
        }
        return "Sync request failed";
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) {
            return "{}";
        }
        try (InputStream stream = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 65536) {
                    throw new IOException("Sync response is too large");
                }
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private boolean isConnected() {
        CloudflareSyncClient active = client;
        return active != null && active.isOpen();
    }

    private CloudflareSyncClient requireSocket() {
        CloudflareSyncClient active = client;
        if (active == null || !active.isOpen()) {
            throw new IllegalArgumentException("Sync is not connected");
        }
        return active;
    }

    private boolean configured() {
        return config != null && !online.endpoint().isEmpty() && !config.channelId().isEmpty();
    }

    private String serviceEndpoint() {
        if (online.endpoint().trim().isEmpty()) {
            throw new IllegalArgumentException("Configure an online service URL first");
        }
        String endpoint = normalizeEndpoint(online.endpoint());
        validateEndpoint(endpoint);
        return endpoint;
    }

    private void resetChannel() {
        closeSocket();
        config.channelId("");
        config.clearInviteTokens();
        invites = new JsonArray();
        joinMode = "open";
        members = new JsonArray();
        remoteProfiles = new JsonArray();
        latestRelations = new JsonArray();
        writeProfiles = false;
        writeRelations = false;
        initialSnapshotApplied = false;
        relationSyncPending = false;
        synchronized (knownRelations) {
            knownRelations.clear();
        }
        saveConfig();
        status = "unconfigured";
        message = null;
        notifyListeners();
    }

    private synchronized void saveConfig() {
        try {
            config.save(configPath);
        } catch (IOException failure) {
            message = "Unable to save sync configuration";
            LOGGER.log(Level.WARNING, "Unable to save sync configuration", failure);
        }
    }

    private void fail(Throwable failure) {
        status = "error";
        message = failure.getMessage() == null ? "Sync request failed" : failure.getMessage();
        notifyListeners();
        LOGGER.log(Level.WARNING, "Sync request failed", failure);
    }

    private void runOnClient(Runnable task) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (started) {
                task.run();
            }
        });
    }

    private void requireStarted() {
        if (!started) {
            throw new IllegalStateException("Sync is not started");
        }
    }

    private void requireNoChannel() {
        if (joining.get()) {
            throw new IllegalArgumentException("Channel request is in progress");
        }
        if (!config.channelId().isEmpty()) {
            throw new IllegalArgumentException("Leave the channel before changing the service URL");
        }
    }

    private void notifyListeners() {
        Runnable[] current;
        synchronized (this) {
            current = listeners.toArray(new Runnable[listeners.size()]);
        }
        for (Runnable listener : current) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Sync listener failed", failure);
            }
        }
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = requireText(endpoint, "Endpoint is required").trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void validateEndpoint(String endpoint) {
        URI parsed = URI.create(normalizeEndpoint(endpoint));
        if (!"http".equals(parsed.getScheme()) && !"https".equals(parsed.getScheme())) {
            throw new IllegalArgumentException("Base URL must use http or https");
        }
        if (parsed.getHost() == null) {
            throw new IllegalArgumentException("Base URL must include a host");
        }
    }

    private static URI socketUri(String endpoint, String channelId) {
        String scheme = endpoint.startsWith("https://") ? "wss://" : "ws://";
        return URI.create(scheme + endpoint.substring(endpoint.indexOf("://") + 3)
                + "/api/channels/" + channelId + "/socket");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Sync snapshot is missing " + key);
        }
        return value.getAsJsonArray();
    }

    private static JsonArray copy(JsonArray source) {
        return new JsonParser().parse(source.toString()).getAsJsonArray();
    }
}
