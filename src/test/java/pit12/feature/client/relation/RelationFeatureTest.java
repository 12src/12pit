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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static pit12.feature.client.relation.api.Relation.ENEMY;
import static pit12.feature.client.relation.api.Relation.FRIEND;
import static pit12.feature.client.relation.api.Relation.NONE;
import static pit12.feature.client.relation.api.RelationReadiness.READY;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import pit12.feature.client.relation.api.Relation;
import pit12.feature.client.relation.api.RelationEntry;
import pit12.feature.client.relation.api.RelationListener;
import pit12.feature.client.relation.storage.RelationStorage;
import pit12.runtime.languages.Languages;
import pit12.runtime.player.TabPresence;
import pit12.runtime.player.TabPresenceListener;
import pit12.shared.concurrent.ClientThread;

public final class RelationFeatureTest {
    private final Thread mainThread = Thread.currentThread();
    private final BlockingQueue<Runnable> clientTasks = new LinkedBlockingQueue<>();
    private final ClientThread client =
            new ClientThread(() -> Thread.currentThread() == mainThread, clientTasks::add);
    private final FakePresence presence = new FakePresence();
    private final MemoryStorage storage = new MemoryStorage();
    private final Map<String, MojangProfileLookup.Profile> profiles = new LinkedHashMap<>();
    private final RelationFeature feature = new RelationFeature(presence, Paths.get("relations"),
            client, storage, profiles::get, () -> 0L, false, new Languages(client));

    @After
    public void stopFeature() {
        feature.stop();
    }

    @Test
    public void loadsRelationsPromotesOnlinePendingPlayerAndNotifiesListeners() throws Exception {
        UUID friendId = new UUID(0, 1);
        UUID onlineEnemyId = new UUID(0, 2);
        storage.stored = Arrays.asList(new RelationEntry(friendId, "foo", FRIEND),
                new RelationEntry(null, "bar", ENEMY));
        presence.online.put(onlineEnemyId, "Bar");
        CountDownLatch loaded = new CountDownLatch(1);
        List<String> changes = new ArrayList<>();
        feature.addListener(new RelationListener() {
            @Override
            public void onRelationChanged(UUID playerId, Relation previous, Relation current) {
                changes.add(playerId + ":" + previous + "->" + current);
            }

            @Override
            public void onRelationsLoaded() {
                loaded.countDown();
            }
        });
        feature.start();
        awaitClient(() -> feature.readiness() == READY);
        assertEquals(0L, loaded.getCount());
        assertEquals(FRIEND, feature.relationOf(friendId));
        assertEquals(ENEMY, feature.relationOf(onlineEnemyId));
        assertTrue(changes.contains(onlineEnemyId + ":NONE->ENEMY"));
        assertNotNull(storage.writes.poll(5, SECONDS));
    }

    @Test
    public void resolvesOfflineAdditionAndFlushesDirtyEntriesOnStop() throws Exception {
        UUID resolvedId = new UUID(0, 3);
        profiles.put("baz", new MojangProfileLookup.Profile(resolvedId, "Baz"));
        feature.start();
        awaitClient(() -> feature.readiness() == READY);
        assertTrue(feature.change(FRIEND, "add", "baz").succeeded());
        awaitClient(() -> feature.relationOf(resolvedId) == FRIEND);
        feature.stop();
        List<RelationEntry> stored = storage.stored;
        assertEquals(1, stored.size());
        RelationEntry saved = stored.get(0);
        assertEquals(resolvedId, saved.playerId());
        assertEquals("Baz", saved.name());
        assertEquals(FRIEND, saved.relation());
    }

    @Test
    public void stoppingBeforeQueuedLoadCallbackRunsIgnoresStaleGeneration() throws Exception {
        UUID staleId = new UUID(0, 9);
        storage.stored = Collections.singletonList(new RelationEntry(staleId, "old", FRIEND));
        feature.start();
        Runnable staleLoad = clientTasks.poll(5, SECONDS);
        assertNotNull(staleLoad);
        feature.stop();
        storage.stored = Collections.emptyList();
        feature.start();
        staleLoad.run();
        awaitClient(() -> feature.readiness() == READY);
        assertEquals(NONE, feature.relationOf(staleId));
    }

    private void awaitClient(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            long remaining = deadline - System.nanoTime();
            assertTrue("Timed out waiting for relation state", remaining > 0L);
            Runnable task = clientTasks.poll(remaining, NANOSECONDS);
            assertNotNull("Expected a client task", task);
            task.run();
        }
    }

    private static final class FakePresence implements TabPresence {
        final Map<UUID, String> online = new LinkedHashMap<>();

        @Override
        public boolean contains(UUID playerId) {
            return online.containsKey(playerId);
        }

        @Override
        public Map<UUID, String> players() {
            return online;
        }

        @Override
        public void addListener(TabPresenceListener listener) {}

        @Override
        public void removeListener(TabPresenceListener listener) {}
    }
    private static final class MemoryStorage implements RelationStorage {
        final BlockingQueue<List<RelationEntry>> writes = new LinkedBlockingQueue<>();
        volatile List<RelationEntry> stored = Collections.emptyList();

        @Override
        public List<RelationEntry> load() {
            return new ArrayList<>(stored);
        }

        @Override
        public void write(List<RelationEntry> entries) {
            List<RelationEntry> copy = new ArrayList<>(entries);
            stored = copy;
            writes.add(copy);
        }
    }
}
