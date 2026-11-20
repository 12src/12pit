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
package pit12.feature.client.relation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pit12.feature.client.relation.api.IdentityLookupState.FAILED;
import static pit12.feature.client.relation.api.IdentityLookupState.NOT_FOUND;
import static pit12.feature.client.relation.api.IdentityLookupState.RESOLVING;
import static pit12.feature.client.relation.api.IdentityLookupState.RETRYING;
import static pit12.feature.client.relation.api.IdentityLookupState.WAITING;
import static pit12.feature.client.relation.api.Relation.ENEMY;
import static pit12.feature.client.relation.api.Relation.FRIEND;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.shared.concurrent.ClientThread;

public final class PendingLookupsTest {
    private final Deque<Runnable> worker = new ArrayDeque<>();
    private final Deque<Runnable> callbacks = new ArrayDeque<>();
    private final List<RelationEntry> resolved = new ArrayList<>();
    private final RelationEntry friend = new RelationEntry(null, "foo", FRIEND);
    private long millis = 10_000;

    @Test
    public void anOldResponseCannotResolveOrBlockAReplacementEntry() {
        UUID id = new UUID(0, 1);
        PendingLookups lookups = start(name -> new MojangProfileLookup.Profile(id, name));
        worker.removeFirst().run();
        RelationEntry replacement = new RelationEntry(null, "foo", ENEMY);
        lookups.refresh(Collections.singletonList(replacement));
        callbacks.removeFirst().run();
        assertTrue(resolved.isEmpty());
        assertEquals(WAITING, lookups.stateOf("foo"));
        lookups.tick();
        assertEquals(RESOLVING, lookups.stateOf("foo"));
        completeRequest();
        assertEquals(Collections.singletonList(replacement), resolved);
    }

    @Test
    public void aMissingPlayerDoesNotRetryUntilExplicitlyRequested() {
        PendingLookups lookups = start(name -> null);
        completeRequest();
        millis = 20_000;
        lookups.tick();
        assertEquals(NOT_FOUND, lookups.stateOf("foo"));
        assertTrue(worker.isEmpty());
        lookups.retry("FOO");
        assertEquals(WAITING, lookups.stateOf("foo"));
        lookups.tick();
        assertEquals(RESOLVING, lookups.stateOf("foo"));
    }

    @Test
    public void temporaryFailuresBackOffAndStopAfterThreeAttempts() {
        PendingLookups lookups = start(name -> {
            throw new IOException("Offline");
        });
        completeRequest();
        assertEquals(RETRYING, lookups.stateOf("foo"));
        millis += 999;
        lookups.tick();
        assertEquals(RETRYING, lookups.stateOf("foo"));
        assertTrue(worker.isEmpty());
        millis += 1;
        lookups.tick();
        assertEquals(RESOLVING, lookups.stateOf("foo"));
        completeRequest();
        assertEquals(RETRYING, lookups.stateOf("foo"));
        millis += 4_999;
        lookups.tick();
        assertEquals(RETRYING, lookups.stateOf("foo"));
        millis += 1;
        lookups.tick();
        assertEquals(RESOLVING, lookups.stateOf("foo"));
        completeRequest();
        assertEquals(FAILED, lookups.stateOf("foo"));
        assertFalse(lookups.problems(false).isEmpty());
        millis += 60_000;
        lookups.tick();
        assertEquals(FAILED, lookups.stateOf("foo"));
        assertTrue(worker.isEmpty());
        lookups.retry("foo");
        lookups.tick();
        assertEquals(RESOLVING, lookups.stateOf("foo"));
    }

    private PendingLookups start(IdentityResolver resolver) {
        PendingLookups lookups = new PendingLookups(resolver, worker::addLast,
                new ClientThread(() -> true, callbacks::addLast), () -> millis,
                (entry, profile) -> resolved.add(entry), () -> {
                });
        lookups.refresh(Collections.singletonList(friend));
        lookups.tick();
        return lookups;
    }

    private void completeRequest() {
        worker.removeFirst().run();
        callbacks.removeFirst().run();
    }
}
