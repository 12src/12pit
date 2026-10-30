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
package pit12.feature.misc.chatmacro;

import static pit12.runtime.languages.Languages.source;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.storage.AtomicFile;

final class MacroBindings {
    private static final Logger LOGGER = Logger.getLogger(MacroBindings.class.getName());
    private final ClientThread client;
    private final Path path;
    private final Consumer<String> report;
    private final Map<Integer, String> bindings = new LinkedHashMap<>();
    private ExecutorService worker;
    private boolean loading;
    private boolean ready;

    MacroBindings(ClientThread client, Path path, Consumer<String> report) {
        this.client = client;
        this.path = path;
        this.report = report;
    }

    void start() {
        client.check();
        if (worker != null)
            return;
        loading = true;
        ready = false;
        ExecutorService started = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-chat-macros");
            thread.setDaemon(true);
            return thread;
        });
        worker = started;
        started.execute(() -> {
            Map<Integer, String> loaded;
            try {
                loaded = Files.notExists(path) ? new LinkedHashMap<>()
                        : decode(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            } catch (IOException | SecurityException | JsonParseException
                    | IllegalArgumentException failure) {
                LOGGER.log(Level.WARNING, "Cannot load chat macros", failure);
                client.execute(() -> {
                    if (worker != started)
                        return;
                    loading = false;
                    report.accept(source(
                            "Chat macros could not be loaded. Use /macro clear to reset them"));
                });
                return;
            }
            client.execute(() -> {
                if (worker != started)
                    return;
                bindings.clear();
                bindings.putAll(loaded);
                loading = false;
                ready = true;
            });
        });
    }

    void stop() {
        client.check();
        ExecutorService stopped = worker;
        if (stopped == null)
            return;
        worker = null;
        stopped.shutdown();
        try {
            if (!stopped.awaitTermination(1500, TimeUnit.MILLISECONDS))
                stopped.shutdownNow();
        } catch (InterruptedException interrupted) {
            stopped.shutdownNow();
            Thread.currentThread().interrupt();
        }
        loading = false;
        ready = false;
    }

    String message(int key) {
        return ready ? bindings.get(key) : null;
    }

    Map<Integer, String> entries() {
        return bindings;
    }

    void bind(int key, String message) {
        requireReady();
        checkMessage(message);
        bindings.put(key, message);
        persist();
    }

    boolean remove(int key) {
        requireReady();
        if (bindings.remove(key) == null)
            return false;
        persist();
        return true;
    }

    void clear() {
        client.check();
        if (worker == null || loading)
            throw new IllegalArgumentException("Chat macros are unavailable or still loading");
        bindings.clear();
        ready = true;
        persist();
    }

    void requireReady() {
        client.check();
        if (!ready)
            throw new IllegalArgumentException("Chat macros are not ready");
    }

    private void persist() {
        Map<Integer, String> saved = new LinkedHashMap<>(bindings);
        ExecutorService writer = worker;
        writer.execute(() -> {
            JsonObject root = new JsonObject();
            for (Map.Entry<Integer, String> entry : saved.entrySet())
                root.addProperty(Keyboard.getKeyName(entry.getKey()), entry.getValue());
            try {
                AtomicFile.write(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            } catch (IOException | SecurityException failure) {
                LOGGER.log(Level.WARNING, "Cannot save chat macros", failure);
                client.execute(() -> {
                    if (worker == writer)
                        report.accept(source("Chat macros could not be saved"));
                });
            }
        });
    }

    private static Map<Integer, String> decode(String text) {
        JsonElement parsed = new JsonParser().parse(text);
        if (!parsed.isJsonObject())
            throw new IllegalArgumentException("Expected chat macro bindings");
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
            int key = Keyboard.getKeyIndex(entry.getKey().toUpperCase(Locale.ROOT));
            if (key == Keyboard.KEY_NONE || result.containsKey(key))
                throw new IllegalArgumentException("Invalid macro key");
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Expected a chat message");
            String message = value.getAsString();
            checkMessage(message);
            result.put(key, message);
        }
        return result;
    }

    private static void checkMessage(String message) {
        if (message.trim().isEmpty() || message.length() > 100)
            throw new IllegalArgumentException("Chat messages must contain 1 to 100 characters");
        for (int index = 0; index < message.length(); index++) {
            if (!ChatAllowedCharacters.isAllowedCharacter(message.charAt(index)))
                throw new IllegalArgumentException(
                        "The chat message contains an invalid character");
        }
    }
}
