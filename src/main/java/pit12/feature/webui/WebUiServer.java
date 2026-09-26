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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import pit12.bootstrap.BuildConfig;
import pit12.feature.profile.api.ProfileCreateSession;
import pit12.feature.profile.api.ProfileMutationResult;
import pit12.feature.profile.api.ProfileSummary;
import pit12.feature.profile.api.Profiles;
import pit12.feature.profile.api.ProfilesSnapshot;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.api.RelationEntry;
import pit12.feature.relation.api.Relations;
import pit12.feature.sync.api.Sync;
import pit12.runtime.config.ChoiceSetting;
import pit12.runtime.config.ConfigCatalog;
import pit12.runtime.config.ConfigChangeListener;
import pit12.runtime.config.ConfigOption;
import pit12.runtime.config.FeatureConfig;
import pit12.runtime.config.NumberSetting;
import pit12.runtime.config.Setting;

final class WebUiServer {
    private static final String RESOURCES = "assets/pit12/web";
    private static final String BUILD_LABEL = buildLabel();
    private static final int PORT = 60916;
    private static final int MAX_STREAMS = 8;
    private static final byte[] CONNECTED = ": connected\n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] UPDATE = "data: update\n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HEARTBEAT = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);
    private final Minecraft minecraft;
    private final ConfigCatalog catalog;
    private final Profiles profiles;
    private final Relations relations;
    private final Sync sync;
    private final SettingsTransfer transfer;
    private final Gson gson = new Gson();
    private final Set<BlockingQueue<Boolean>> streams = new HashSet<BlockingQueue<Boolean>>();
    private final ConfigChangeListener configListener = ignored -> notifyStreams();
    private final Runnable profileListener = this::notifyStreams;
    private final Runnable relationListener = this::notifyStreams;
    private final Runnable syncListener = this::notifyStreams;
    private HttpServer server;
    private ExecutorService executor;

    WebUiServer(Minecraft minecraft, ConfigCatalog catalog, Profiles profiles, Relations relations,
            Sync sync) {
        this.minecraft = minecraft;
        this.catalog = catalog;
        this.profiles = profiles;
        this.relations = relations;
        this.sync = sync;
        transfer = new SettingsTransfer(profiles, relations);
    }

    void start() throws IOException {
        if (server != null) {
            return;
        }
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpServer created;
        try {
            created = HttpServer.create(new InetSocketAddress(loopback, PORT), 0);
        } catch (BindException occupied) {
            created = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        }
        executor = Executors.newFixedThreadPool(MAX_STREAMS + 2, runnable -> {
            Thread thread = new Thread(runnable, "12pit-web-server");
            thread.setDaemon(true);
            return thread;
        });
        created.setExecutor(executor);
        created.createContext("/", this::handle);
        created.start();
        server = created;
        catalog.addListener(configListener);
        profiles.addListener(profileListener);
        relations.addChangeListener(relationListener);
        sync.addListener(syncListener);
    }

    void stop() {
        catalog.removeListener(configListener);
        profiles.removeListener(profileListener);
        relations.removeChangeListener(relationListener);
        sync.removeListener(syncListener);
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    URI address() {
        if (server == null) {
            throw new IllegalStateException("Settings server is not running");
        }
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                serveResource(exchange, path);
            } else {
                sendJson(exchange, 405, object("error", "Method not allowed"));
            }
        } catch (JsonParseException failure) {
            sendJson(exchange, 400, object("error", "Invalid JSON"));
        } catch (IllegalArgumentException failure) {
            sendJson(exchange, 400, object("error", failure.getMessage()));
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IllegalArgumentException) {
                sendJson(exchange, 400, object("error", cause.getMessage()));
            } else {
                sendJson(exchange, 500, object("error", "Settings operation failed"));
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 503, object("error", "Settings server is stopping"));
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange, String path)
            throws IOException, ExecutionException, InterruptedException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("GET".equals(exchange.getRequestMethod()) && "/api/events".equals(path)) {
            serveEvents(exchange);
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && "/api/state".equals(path)) {
            sendJson(exchange, 200, onClient(this::state));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod()) || !"/api/setting".equals(path)
                && !"/api/profile".equals(path) && !"/api/relation".equals(path)
                && !"/api/sync".equals(path) && !"/api/online".equals(path)
                && !"/api/transfer/export".equals(path) && !"/api/transfer/preview".equals(path)
                && !"/api/transfer/apply".equals(path)) {
            sendJson(exchange, 404, object("error", "Unknown endpoint"));
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin
                .equals(address().toString().substring(0, address().toString().length() - 1))) {
            sendJson(exchange, 403, object("error", "Unexpected origin"));
            return;
        }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) {
            sendJson(exchange, 415, object("error", "Expected JSON"));
            return;
        }
        JsonElement parsed = new JsonParser().parse(new String(
                readLimited(exchange.getRequestBody(),
                        path.startsWith("/api/transfer/") ? 4 * 1024 * 1024
                                : "/api/sync".equals(path) ? 128 * 1024
                                        : "/api/relation".equals(path) ? 32768 : 8192),
                StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        JsonObject request = parsed.getAsJsonObject();
        sendJson(exchange, 200, onClient(() -> {
            if ("/api/transfer/export".equals(path)) {
                return transfer.exportData(request);
            }
            if ("/api/transfer/preview".equals(path)) {
                return transfer.preview(request);
            }
            if ("/api/transfer/apply".equals(path)) {
                transfer.apply(request);
                return state();
            }
            if ("/api/relation".equals(path)) {
                List<Object> results = changeRelations(request);
                return object("state", state(), "results", results);
            }
            if ("/api/sync".equals(path)) {
                changeSync(request);
                return state();
            } else if ("/api/online".equals(path)) {
                String action = requiredString(request, "action");
                if ("provider".equals(action)) {
                    sync.setProvider(requiredString(request, "provider"));
                } else if ("region".equals(action)) {
                    sync.setRegion(requiredString(request, "region"));
                } else if ("selfHostedUrl".equals(action)) {
                    sync.setSelfHostedUrl(requiredString(request, "selfHostedUrl"));
                } else if ("nickname".equals(action)) {
                    sync.setNickname(requiredString(request, "nickname"));
                } else {
                    throw new IllegalArgumentException("Unknown online action");
                }
                return state();
            }
            if ("/api/setting".equals(path)) {
                changeSetting(request);
            } else {
                changeProfile(request);
            }
            return state();
        }));
    }

    private void serveEvents(HttpExchange exchange) throws IOException {
        BlockingQueue<Boolean> signal = new ArrayBlockingQueue<Boolean>(1);
        synchronized (streams) {
            if (streams.size() >= MAX_STREAMS) {
                sendJson(exchange, 503, object("error", "Too many open pages"));
                return;
            }
            streams.add(signal);
        }
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(CONNECTED);
                output.flush();
                while (!Thread.currentThread().isInterrupted()) {
                    output.write(signal.poll(5, TimeUnit.SECONDS) == null ? HEARTBEAT : UPDATE);
                    output.flush();
                }
            }
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (streams) {
                streams.remove(signal);
            }
        }
    }

    private void notifyStreams() {
        synchronized (streams) {
            for (BlockingQueue<Boolean> signal : streams) {
                signal.offer(Boolean.TRUE);
            }
        }
    }

    private <T> T onClient(java.util.concurrent.Callable<T> action)
            throws ExecutionException, InterruptedException {
        // ConfigCatalog and Profiles are confined to Minecraft's client thread.
        return minecraft.addScheduledTask(action).get();
    }

    private void changeSetting(JsonObject request) {
        String featureId = requiredString(request, "featureId");
        String settingId = requiredString(request, "settingId");
        FeatureConfig feature = catalog.feature(featureId);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature");
        }
        JsonElement value = request.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected a setting value");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if ("enabled".equals(settingId) && feature.toggleable()) {
            if (!primitive.isBoolean()) {
                throw new IllegalArgumentException("Expected a boolean");
            }
            feature.setEnabled(primitive.getAsBoolean());
            return;
        }
        for (ConfigOption<?> option : feature.options()) {
            if (!option.setting().id().equals(settingId)) {
                continue;
            }
            Object candidate;
            if (option.kind() == ConfigOption.Kind.KEYBIND) {
                if (!primitive.isString()) {
                    throw new IllegalArgumentException("Expected a key name");
                }
                String name = primitive.getAsString();
                int code = Keyboard.getKeyIndex(name);
                if (code == Keyboard.KEY_NONE && !"NONE".equals(name)) {
                    throw new IllegalArgumentException("Unknown key");
                }
                candidate = Integer.valueOf(code);
            } else if (primitive.isBoolean()) {
                candidate = Boolean.valueOf(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                candidate = primitive.getAsNumber();
            } else {
                throw new IllegalArgumentException("Expected a number or boolean");
            }
            setValue(option.setting(), candidate);
            return;
        }
        throw new IllegalArgumentException("Unknown setting");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setValue(Setting<?> setting, Object value) {
        ((Setting) setting).set(value);
    }

    private void changeProfile(JsonObject request) {
        String action = requiredString(request, "action");
        ProfileMutationResult result;
        if ("create".equals(action)) {
            result = profiles.beginCreate();
            if (!result.succeeded()) {
                throw new IllegalArgumentException(result.message());
            }
            ProfileCreateSession session = result.createSession();
            try {
                session.setName(requiredString(request, "name"));
                result = session.commit();
            } finally {
                if (!session.isClosed()) {
                    session.cancel();
                }
            }
        } else {
            UUID id = UUID.fromString(requiredString(request, "id"));
            switch (action) {
                case "switch":
                    result = profiles.switchTo(id);
                    break;
                case "rename":
                    result = profiles.rename(id, requiredString(request, "name"));
                    break;
                case "delete":
                    result = profiles.delete(id);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown profile action");
            }
        }
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.message());
        }
    }

    private void changeSync(JsonObject request) {
        String action = requiredString(request, "action");
        if ("regenerateKey".equals(action)) {
            sync.regenerateKey();
        } else if ("importKey".equals(action)) {
            sync.importKey(requiredString(request, "privateKey"));
        } else if ("create".equals(action)) {
            sync.createChannel();
        } else if ("join".equals(action)) {
            sync.joinChannel(requiredString(request, "channelId"),
                    optionalString(request, "inviteToken"), optionalString(request, "password"));
        } else if ("invite".equals(action)) {
            sync.requestInvite();
        } else if ("revokeInvite".equals(action)) {
            sync.revokeInvite(requiredString(request, "inviteId"));
        } else if ("joinPolicy".equals(action)) {
            sync.setJoinPolicy(requiredString(request, "mode"),
                    optionalString(request, "password"));
        } else if ("leave".equals(action)) {
            sync.leaveChannel();
        } else if ("dissolve".equals(action)) {
            sync.dissolveChannel();
        } else if ("profiles".equals(action)) {
            sync.refreshProfiles();
        } else if ("importProfile".equals(action)) {
            sync.importProfile(requiredString(request, "ownerId"),
                    requiredString(request, "profileId"));
        } else if ("uploads".equals(action)) {
            sync.setUploads(requiredString(request, "profiles"),
                    optionalBoolean(request, "relations"));
        } else if ("selection".equals(action)) {
            sync.setSyncSelection(optionalBoolean(request, "profiles"),
                    optionalBoolean(request, "relations"));
        } else if ("access".equals(action)) {
            sync.setAccess(requiredString(request, "memberId"), requiredString(request, "role"),
                    optionalBoolean(request, "writeProfiles"),
                    optionalBoolean(request, "writeRelations"));
        } else if ("remove".equals(action)) {
            sync.removeMember(requiredString(request, "memberId"));
        } else {
            throw new IllegalArgumentException("Unknown sync action");
        }
    }

    private List<Object> changeRelations(JsonObject request) {
        if (sync.relationsReadOnly()) {
            throw new IllegalArgumentException(
                    "Relations are read-only while synced without write permission");
        }
        String problem = relations.readinessProblem();
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        Relation relation = Relation.valueOf(requiredString(request, "relation"));
        if (relation == Relation.NONE) {
            throw new IllegalArgumentException("Choose Friend or Enemy");
        }
        String action = requiredString(request, "action");
        if (!"add".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Unknown relation action");
        }
        JsonElement value = request.get("entries");
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Expected a list of players");
        }
        JsonArray entries = value.getAsJsonArray();
        if (entries.size() > 100) {
            throw new IllegalArgumentException("Select at most 100 players at a time");
        }
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Expected a player entry");
            }
            JsonObject entry = element.getAsJsonObject();
            requiredString(entry, "name");
            JsonElement idValue = entry.get("uuid");
            if (idValue != null && !idValue.isJsonNull()) {
                if ("add".equals(action)) {
                    throw new IllegalArgumentException("Cannot add a player by UUID");
                }
                UUID.fromString(requiredString(entry, "uuid"));
            }
        }
        List<Object> results = new ArrayList<Object>(Collections.nCopies(entries.size(), null));
        List<RelationEntry> changes = new ArrayList<RelationEntry>();
        List<Integer> positions = new ArrayList<Integer>();
        Set<String> seen = new HashSet<String>();
        Set<String> existing = new HashSet<String>();
        if ("add".equals(action)) {
            for (RelationEntry saved : relations.entries(relation)) {
                existing.add(saved.name().toLowerCase(Locale.ROOT));
            }
        }
        for (int index = 0; index < entries.size(); index++) {
            JsonElement element = entries.get(index);
            JsonObject entry = element.getAsJsonObject();
            String name = requiredString(entry, "name").trim();
            JsonElement idValue = entry.get("uuid");
            UUID id = idValue == null || idValue.isJsonNull() ? null
                    : UUID.fromString(requiredString(entry, "uuid"));
            String identity = id == null ? name.toLowerCase(Locale.ROOT) : id.toString();
            if (name.isEmpty() || name.length() > 48
                    || id == null && "add".equals(action) && !name.matches("[A-Za-z0-9_]{1,48}")) {
                results.set(index, object("name", name, "message", "Invalid player name"));
            } else if (!seen.add(identity)) {
                results.set(index, object("name", name, "message", "Duplicate entry skipped"));
            } else if ("add".equals(action) && existing.contains(identity)) {
                results.set(index, object("name", name, "message", "Already on this list"));
            } else {
                changes.add(new RelationEntry(id, name, relation));
                positions.add(Integer.valueOf(index));
            }
        }
        List<String> messages = relations.changeMany(relation, action, changes);
        for (int index = 0; index < messages.size(); index++) {
            results.set(positions.get(index).intValue(),
                    object("name", changes.get(index).name(), "message", messages.get(index)));
        }
        return results;
    }

    private Map<String, Object> state() {
        List<Object> features = new ArrayList<Object>();
        for (FeatureConfig feature : catalog.features()) {
            List<Object> sections = new ArrayList<Object>();
            Map<String, Map<String, Object>> byId =
                    new LinkedHashMap<String, Map<String, Object>>();
            for (ConfigOption<?> option : feature.options()) {
                String sectionId =
                        option.subcategory() == null ? "settings" : option.subcategory().id();
                Map<String, Object> section = byId.get(sectionId);
                if (section == null) {
                    section = object("id", sectionId, "name",
                            option.subcategory() == null ? "Settings"
                                    : option.subcategory().displayName(),
                            "options", new ArrayList<Object>());
                    byId.put(sectionId, section);
                    sections.add(section);
                }
                @SuppressWarnings("unchecked")
                List<Object> options = (List<Object>) section.get("options");
                options.add(optionState(option));
            }
            features.add(object("id", feature.id(), "name", feature.displayName(), "description",
                    feature.description(), "categoryId", feature.category().id(), "category",
                    feature.category().displayName(), "toggleable", feature.toggleable(), "enabled",
                    feature.enabled(), "sections", sections));
        }
        ProfilesSnapshot snapshot = profiles.snapshot();
        List<Object> entries = new ArrayList<Object>();
        for (ProfileSummary profile : snapshot.profiles()) {
            entries.add(object("id", profile.id().toString(), "name", profile.name()));
        }
        List<Object> relationEntries = new ArrayList<Object>();
        for (Relation type : new Relation[] {Relation.FRIEND, Relation.ENEMY}) {
            for (RelationEntry entry : relations.entries(type)) {
                relationEntries.add(object("uuid",
                        entry.playerId() == null ? null : entry.playerId().toString(), "name",
                        entry.name(), "relation", type.name()));
            }
        }
        return object("version", BUILD_LABEL, "features", features, "relations",
                object("problem", relations.readinessProblem(), "entries", relationEntries),
                "online", sync.onlineState(), "sync", sync.state(), "profiles",
                object("loadState", snapshot.loadState().name(), "activeId",
                        snapshot.activeProfileId() == null ? null
                                : snapshot.activeProfileId().toString(),
                        "entries", entries, "problems", snapshot.problems(), "unpersisted",
                        snapshot.hasUnpersistedChanges()));
    }

    private static String buildLabel() {
        if (BuildConfig.RELEASE_BUILD) {
            return BuildConfig.VERSION;
        }
        if (!BuildConfig.GIT_COMMIT.isEmpty()) {
            return BuildConfig.GIT_COMMIT.substring(0,
                    Math.min(7, BuildConfig.GIT_COMMIT.length()));
        }
        return "dev";
    }

    private Map<String, Object> optionState(ConfigOption<?> option) {
        Setting<?> setting = option.setting();
        Map<String, Object> value = object("id", setting.id(), "name", setting.displayName(),
                "description", setting.description(), "kind", option.kind().name(), "value",
                setting.get());
        if (setting instanceof NumberSetting<?>) {
            NumberSetting<?> number = (NumberSetting<?>) setting;
            value.put("min", number.minimumValue());
            value.put("max", number.maximumValue());
            value.put("step", number.stepValue());
        }
        if (setting instanceof ChoiceSetting) {
            List<Object> choices = new ArrayList<Object>();
            for (ChoiceSetting.Choice choice : ((ChoiceSetting) setting).choices()) {
                choices.add(object("value", choice.value(), "name", choice.displayName()));
            }
            value.put("choices", choices);
        }
        if (option.kind() == ConfigOption.Kind.KEYBIND) {
            String name = Keyboard.getKeyName(((Number) setting.get()).intValue());
            value.put("keyName", name == null ? "NONE" : name);
        }
        return value;
    }

    private void serveResource(HttpExchange exchange, String path) throws IOException {
        if ("/".equals(path)) {
            path = "/index.html";
        }
        if (!"/index.html".equals(path) && !path.startsWith("/assets/") || path.contains("..")
                || path.contains("\\")) {
            sendJson(exchange, 404, object("error", "Not found"));
            return;
        }
        try (InputStream resource =
                WebUiServer.class.getClassLoader().getResourceAsStream(RESOURCES + path)) {
            if (resource == null) {
                sendJson(exchange, 404, object("error", "Not found"));
                return;
            }
            String type = path.endsWith(".html") ? "text/html; charset=utf-8"
                    : path.endsWith(".js") ? "text/javascript; charset=utf-8"
                            : path.endsWith(".css") ? "text/css; charset=utf-8"
                                    : path.endsWith(".png") ? "image/png"
                                            : "application/octet-stream";
            sendBytes(exchange, 200, type, readLimited(resource, 4 * 1024 * 1024));
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8",
                gson.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String type, byte[] bytes)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = input.read(chunk)) != -1) {
            if (bytes.size() + count > limit) {
                throw new IllegalArgumentException("Request or resource is too large");
            }
            bytes.write(chunk, 0, count);
        }
        return bytes.toByteArray();
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected " + key);
        }
        return value.getAsString();
    }

    private static boolean optionalBoolean(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null)
            return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected " + key);
        }
        return value.getAsString();
    }

    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        for (int index = 0; index < pairs.length; index += 2) {
            value.put((String) pairs[index], pairs[index + 1]);
        }
        return value;
    }
}
