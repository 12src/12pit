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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pit12.feature.relation.api.IdentityLookupState;
import pit12.feature.relation.api.Relation;
import pit12.feature.relation.storage.RelationStore;
import pit12.shared.concurrent.ClientThread;

public final class RelationLookupPersistenceTest {
    @Rule
    public final TemporaryFolder files = new TemporaryFolder();

    @Test
    public void loadedPendingIdentityCanBeLookedUpAndSaved() throws Exception {
        Path path = files.getRoot().toPath().resolve("relations.json");
        Files.write(path,
                ("{\"schemaVersion\":1,\"entries\":["
                        + "{\"name\":\"PendingPlayer\",\"relation\":\"FRIEND\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        RelationStore store = new RelationStore(path);
        RelationBook book = new RelationBook();
        book.replace(store.load());
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<Runnable> tasks = new ArrayList<>();
        PendingLookups lookups = new PendingLookups(name -> {
            assertEquals("PendingPlayer", name);
            return new MojangProfileLookup.Profile(id, name);
        }, tasks::add, ClientThread.current(), () -> 0L,
                (entry, profile) -> book.bind(entry, profile.id, profile.name), () -> {
                });
        lookups.refresh(book.entries());
        assertEquals(IdentityLookupState.WAITING, lookups.stateOf("PendingPlayer"));
        assertEquals(Relation.NONE, book.relationOf(id));
        lookups.tick();
        assertEquals(IdentityLookupState.RESOLVING, lookups.stateOf("PendingPlayer"));
        assertEquals(1, tasks.size());
        tasks.remove(0).run();
        assertEquals(Relation.FRIEND, book.relationOf(id));
        assertNull(book.pending("PendingPlayer"));
        store.write(book.entries());
        assertEquals(id, store.load().get(0).playerId());
    }

    @Test
    public void loadedPendingIdentityCanBeConfirmedByTab() throws Exception {
        Path path = files.getRoot().toPath().resolve("relations.json");
        Files.write(path,
                ("{\"schemaVersion\":1,\"entries\":["
                        + "{\"name\":\"PendingPlayer\",\"relation\":\"ENEMY\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        RelationStore store = new RelationStore(path);
        RelationBook book = new RelationBook();
        book.replace(store.load());
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(book.observe(id, "PendingPlayer"));
        assertEquals(Relation.ENEMY, book.relationOf(id));
        assertNull(book.pending("PendingPlayer"));
        store.write(book.entries());
        assertEquals(id, store.load().get(0).playerId());
    }
}
