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

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import pit12.feature.swap.api.SwapBindings;
import pit12.shared.concurrent.ClientThread;
import pit12.shared.event.Listeners;
import pit12.shared.result.OperationResult;
import pit12.shared.result.OperationResult.Status;

final class BindingBook implements SwapBindings {
    private static final Logger LOGGER = Logger.getLogger(BindingBook.class.getName());
    private final ClientThread client;
    private final BindingStore store;
    private final LinkedHashMap<ItemIdentity, SwapBinding> bindings = new LinkedHashMap<>();
    private final Set<Integer> keys = new HashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Consumer<String> report;
    private ExecutorService worker;
    private boolean loading = true;
    private String problem = source("Swap bindings are loading");
    private long revision;

    BindingBook(ClientThread client, Path path, Consumer<String> report) {
        this.client = client;
        store = new BindingStore(path);
        this.report = report;
    }

    void start() {
        client.check();
        if (worker != null)
            return;
        loading = true;
        problem = source("Swap bindings are loading");
        ExecutorService started = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "12pit-swap-bindings");
            thread.setDaemon(true);
            return thread;
        });
        worker = started;
        started.execute(() -> {
            List<SwapBinding> loaded;
            try {
                loaded = store.load();
            } catch (IOException | SecurityException | JsonParseException
                    | IllegalArgumentException failure) {
                LOGGER.log(Level.WARNING, "Cannot load swap bindings", failure);
                client.execute(() -> {
                    if (worker != started)
                        return;
                    loading = false;
                    problem = source(
                            "Swap bindings could not be loaded; import a file or use /swap clear");
                    report.accept(problem);
                    changed();
                });
                return;
            }
            client.execute(() -> {
                if (worker != started)
                    return;
                bindings.clear();
                for (SwapBinding binding : loaded)
                    bindings.put(binding.identity, binding);
                loading = false;
                problem = null;
                changed();
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
        problem = source("Swap bindings are unavailable");
        loading = false;
    }

    Collection<SwapBinding> entries() {
        client.check();
        return bindings.values();
    }

    List<SwapBinding> forKey(int key) {
        client.check();
        List<SwapBinding> result = new ArrayList<>();
        if (problem == null) {
            for (SwapBinding binding : bindings.values()) {
                if (binding.key == key)
                    result.add(binding);
            }
        }
        return result;
    }

    boolean hasKey(int key) {
        client.check();
        return problem == null && keys.contains(key);
    }

    SwapBinding matching(ItemIdentity identity) {
        client.check();
        return bindings.get(identity);
    }

    void bind(SwapBinding binding) {
        requireReady();
        bindings.put(binding.identity, binding);
        persist();
    }

    int unbind(int key, ItemIdentity identity) {
        requireReady();
        int before = bindings.size();
        bindings.values().removeIf(
                binding -> key > 0 ? binding.key == key : binding.identity.equals(identity));
        int removed = before - bindings.size();
        if (removed > 0)
            persist();
        return removed;
    }

    void clear() {
        client.check();
        if (worker == null || loading) {
            throw new IllegalArgumentException(problem);
        }
        bindings.clear();
        problem = null;
        persist();
    }

    private void requireReady() {
        client.check();
        if (problem != null)
            throw new IllegalArgumentException(problem);
    }

    private void persist() {
        changed();
        List<SwapBinding> saved = new ArrayList<>(bindings.values());
        ExecutorService writer = worker;
        writer.execute(() -> {
            try {
                store.write(saved);
            } catch (IOException | SecurityException failure) {
                LOGGER.log(Level.WARNING, "Cannot save swap bindings", failure);
                client.execute(() -> {
                    if (worker == writer)
                        report.accept(source("Swap bindings could not be saved"));
                });
            }
        });
    }

    private void changed() {
        keys.clear();
        for (SwapBinding binding : bindings.values())
            keys.add(binding.key);
        revision++;
        Listeners.notify(listeners, Runnable::run);
    }

    @Override
    public String readinessProblem() {
        client.check();
        return problem;
    }

    @Override
    public long revision() {
        client.check();
        return revision;
    }

    @Override
    public int count() {
        client.check();
        return bindings.size();
    }

    @Override
    public OperationResult<String> exportBindings() {
        client.check();
        return problem == null ? OperationResult.success(BindingStore.encode(entries()))
                : OperationResult.failure(Status.UNAVAILABLE, problem);
    }

    @Override
    public OperationResult<Void> validateImport(String data) {
        client.check();
        if (worker == null || loading) {
            return OperationResult.failure(Status.UNAVAILABLE, problem);
        }
        try {
            BindingStore.decode(data);
            return OperationResult.success(null);
        } catch (JsonParseException | IllegalArgumentException failure) {
            return OperationResult.failure(Status.INVALID_VALUE,
                    "Invalid swap bindings: " + failure.getMessage());
        }
    }

    @Override
    public OperationResult<Void> replaceBindings(String data) {
        client.check();
        if (worker == null || loading) {
            return OperationResult.failure(Status.UNAVAILABLE, problem);
        }
        List<SwapBinding> replacement;
        try {
            replacement = BindingStore.decode(data);
        } catch (JsonParseException | IllegalArgumentException failure) {
            return OperationResult.failure(Status.INVALID_VALUE,
                    "Invalid swap bindings: " + failure.getMessage());
        }
        bindings.clear();
        for (SwapBinding binding : replacement)
            bindings.put(binding.identity, binding);
        problem = null;
        persist();
        return OperationResult.success(null);
    }

    @Override
    public void addChangeListener(Runnable listener) {
        client.check();
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    @Override
    public void removeChangeListener(Runnable listener) {
        client.check();
        listeners.remove(listener);
    }
}
