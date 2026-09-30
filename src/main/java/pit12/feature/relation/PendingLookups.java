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

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import pit12.feature.relation.api.IdentityLookupState;
import pit12.feature.relation.api.RelationEntry;
import pit12.shared.concurrent.ClientThread;

final class PendingLookups {
    private final Map<RelationEntry, Request> requests = new IdentityHashMap<>();
    private final IdentityResolver resolver;
    private final Executor worker;
    private final ClientThread client;
    private final LongSupplier time;
    private final BiConsumer<RelationEntry, MojangProfileLookup.Profile> resolved;
    private final Runnable changed;
    private Request running;

    PendingLookups(IdentityResolver resolver, Executor worker, ClientThread client,
            LongSupplier time, BiConsumer<RelationEntry, MojangProfileLookup.Profile> resolved,
            Runnable changed) {
        this.resolver = resolver;
        this.worker = worker;
        this.client = client;
        this.time = time;
        this.resolved = resolved;
        this.changed = changed;
    }

    void refresh(List<RelationEntry> entries) {
        client.check();
        requests.keySet().removeIf(entry -> !entries.contains(entry));
        for (RelationEntry entry : entries) {
            if (entry.playerId() == null && !requests.containsKey(entry))
                requests.put(entry, new Request(entry));
        }
    }

    void retry(String name) {
        client.check();
        for (Request request : requests.values()) {
            if (request.entry.name().equalsIgnoreCase(name)
                    && (request.state == IdentityLookupState.FAILED
                            || request.state == IdentityLookupState.NOT_FOUND)) {
                request.attempts = 0;
                request.deadline = 0L;
                request.state = IdentityLookupState.WAITING;
                changed.run();
            }
        }
    }

    void tick() {
        client.check();
        if (running != null)
            return;
        long now = time.getAsLong();
        for (Request request : requests.values()) {
            if (request.state == IdentityLookupState.WAITING
                    || request.state == IdentityLookupState.RETRYING && now >= request.deadline) {
                running = request;
                request.attempts++;
                request.state = IdentityLookupState.RESOLVING;
                changed.run();
                worker.execute(() -> lookup(request));
                return;
            }
        }
    }

    IdentityLookupState stateOf(String name) {
        client.check();
        for (Request request : requests.values()) {
            if (request.entry.name().equalsIgnoreCase(name))
                return request.state;
        }
        return IdentityLookupState.UNKNOWN;
    }

    List<String> problems() {
        client.check();
        List<String> problems = new ArrayList<>();
        for (Request request : requests.values()) {
            if (request.state == IdentityLookupState.FAILED)
                problems.add("UUID lookup failed for " + request.entry.name()
                        + "; add the player again to retry");
            if (request.state == IdentityLookupState.NOT_FOUND)
                problems.add("Player was not found: " + request.entry.name());
        }
        return problems;
    }

    void clear() {
        client.check();
        requests.clear();
        running = null;
    }

    private void lookup(Request request) {
        MojangProfileLookup.Profile profile;
        try {
            profile = resolver.lookup(request.entry.name());
        } catch (IOException | RuntimeException failure) {
            client.execute(() -> complete(request, null, true));
            return;
        }
        client.execute(() -> complete(request, profile, false));
    }

    private void complete(Request request, MojangProfileLookup.Profile profile, boolean failed) {
        if (running == request)
            running = null;
        if (requests.get(request.entry) != request)
            return;
        if (failed) {
            request.state = request.attempts < 3 ? IdentityLookupState.RETRYING
                    : IdentityLookupState.FAILED;
            request.deadline = time.getAsLong() + (request.attempts == 1 ? 1000L : 5000L);
        } else if (profile == null) {
            request.state = IdentityLookupState.NOT_FOUND;
        } else {
            requests.remove(request.entry);
            resolved.accept(request.entry, profile);
        }
        changed.run();
    }

    private static final class Request {
        final RelationEntry entry;
        IdentityLookupState state = IdentityLookupState.WAITING;
        int attempts;
        long deadline;

        Request(RelationEntry entry) {
            this.entry = entry;
        }
    }
}
